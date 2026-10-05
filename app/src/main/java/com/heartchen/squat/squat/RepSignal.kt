package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType

/**
 * 動作訊號：把一幀關鍵點換算成單一「動作進度」純量。
 *
 * **0 = 站姿靜止，正值 = 往動作方向前進。** 深蹲往下、雙臂高舉往上，
 * 在這個抽象下都是「進度變大」，所以五階段狀態機（轉折點 + 連續幀確認）
 * 只需要一份，不必每個動作各寫一套。
 *
 * ### 為什麼需要這層抽象
 * 原本狀態機直接讀髖部 Y，整條管線都假設「下肢垂直位移」。
 * `ExerciseType.calibration`（`CalibrationKind`）宣告了校正方式卻**從未被讀取**，
 * 於是不管選什麼動作都會被要求「做兩下基準深蹲」—— 對手臂動作毫無意義。
 * 這層抽象把「追蹤什麼量、判準是多少、門檻是多少」交給動作自己宣告，
 * 狀態機則完全不知道自己在數深蹲還是數舉手。
 */
interface RepSignal {
    /** 本幀的動作進度。所需關鍵點缺漏時回傳 null，該幀不更新狀態。 */
    fun progress(points: Map<KeyPointType, KeyPoint>): Float?

    /**
     * 達成判準：進度達到此值視為完成一次標準動作，達成率 `p = 峰值進度 / target`。
     *
     * **深蹲家族回傳 null** —— 它們的分母是校正兩下基準動作得到的個人化深度 `Duser`
     * （論文 3.5），不是固定判準。因此 `target == null` 同時也是
     * 「這個動作需要跑基準校正流程」的唯一判斷依據：不再另外用一個 enum 記一次，
     * 兩處記載才會像 `CalibrationKind` 那樣悄悄失去同步。
     */
    val target: Float?

    /** 進度超過此值視為動作開始（STAND → DOWN）。 */
    val enterThreshold: Float

    /** 須從本次峰值回退此量才算轉折（DOWN → BOTTOM），避免停頓期間被雜訊誤觸發。 */
    val turnConfirmRise: Float

    /** 進度低於此值視為回到站姿（UP → STAND，計次 +1）。 */
    val returnThreshold: Float
}

/**
 * 深蹲／坐站：進度 = 髖部相對站立基準的下降量，以「髖-踝」垂直距離正規化。
 *
 * [target] 為 null，因為深蹲的分母是校正取得的 `Duser`。
 * 三個門檻沿用 M2 以來實測調過的 [Config] 絕對值，**行為與抽象化之前完全相同**
 * （`SquatStateMachineTest` 的四個深蹲迴歸測試即為此而存在）。
 */
/**
 * 追蹤「當下的身體像素尺度」，讓上肢訊號成為**尺度不變**的。
 *
 * ### 為什麼需要
 * 上肢訊號原本是 `(校正時腕肩落差 − 當下腕肩落差) ÷ 校正時肩寬` ——
 * 分子是**逐幀**像素、分母是**校正時**像素，**不是尺度不變的**。
 * 使用者校正完之後往後退，當下落差整體縮小，手垂下時的進度就不再是 0 而是正值：
 *
 * ```
 * 手垂下時的進度 = 判準 × (1 − 校正距離/當下距離)
 * ```
 *
 * 退到校正距離的 **1.25 倍**（2m 校正、站到 2.5m），這個值就等於返回門檻
 * （`0.20 × 判準`），於是 `UP → STAND` **永遠不會發生** ——
 * 一下都不計，而且狀態機永久停在 UP。
 *
 * 2026-10-05 實機證據：第 1 下 12:11:54，第 2 下 12:12:17（**中間 23.8 秒**
 * 舉了很多次毫無反應），之後恢復 3.4~3.8 秒的正常節奏。
 * 另一份逐幀資料（使用者中途退了 6%）直接重現：手垂下的 260 幀裡
 * **236 幀超過返回門檻**；改用本追蹤器後是 **0 幀**。
 *
 * ### 為什麼不直接用逐幀肩寬
 * 實測逐幀肩寬抖動 σ = 11%，直接當分母會把抖動灌進進度。
 * 但**距離變化是慢的（跨步要 0.5 秒以上）、關鍵點抖動是快的（逐幀）**，
 * 用 EMA 就能分開。實測進度的逐幀 σ：
 * 校正肩寬 0.078 ／ **EMA 0.052** ／ 逐幀肩寬 0.055 —— EMA 反而最穩。
 *
 * 時間常數沿用 [Config.EMA_ALPHA]（座標本身就是用它平滑的，約 3 幀 ≈ 0.14 秒）：
 * 對「跨一步」綽綽有餘，對逐幀抖動夠鈍。**不另立常數** ——
 * 同一件事記在兩處就會像 `CalibrationKind` 那樣失去同步。
 *
 * ### 為什麼這不會動到已驗證的行為
 * 初始值就是校正時的肩寬，而且在校正距離上 EMA 會收斂回同一個值 ——
 * 使用者沒有移動時，進度與改動前**完全相同**。
 * 判準與三個門檻仍然用**校正值**算（它們定義的是判準本身，不該隨使用者走動而變）。
 */
internal class ScaleTracker(initial: Float) {
    var value: Float = initial
        private set

    /** 餵入當下量到的尺度；非正值會被忽略（保留上一個可信值）。 */
    fun update(current: Float): Float {
        if (current > 0f) {
            value += Config.EMA_ALPHA * (current - value)
        }
        return value
    }
}

internal fun shoulderWidthOf(points: Map<KeyPointType, KeyPoint>): Float? {
    val left = points[KeyPointType.LEFT_SHOULDER] ?: return null
    val right = points[KeyPointType.RIGHT_SHOULDER] ?: return null
    return kotlin.math.abs(left.x - right.x)
}

class SquatSignal(
    private val standBaselineY: Float,
    private val normalizeScale: Float,
) : RepSignal {
    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (normalizeScale <= 0f) return null
        val hipY = averageY(points, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP) ?: return null
        return (hipY - standBaselineY) / normalizeScale
    }

    override val target: Float? = null
    override val enterThreshold = Config.DOWN_ENTER_RATIO
    override val turnConfirmRise = Config.TURN_CONFIRM_RISE_RATIO
    override val returnThreshold = Config.STAND_RETURN_RATIO
}

/**
 * 雙臂高舉：進度 = 手腕相對站姿的上舉量，以**校正時的肩寬**正規化。
 *
 * ### 判準是「舉到肩高」，而不是個人化基準
 * 手臂高舉有明確的解剖學地標：手腕到達肩線。因此 [target] 取「站姿時手腕低於肩的距離」
 * —— 進度剛好等於 target 時，手腕正好在肩高，`p = 1.0`。這同時是個人化的
 * （用使用者自己的站姿與肩寬量）又是絕對的（錨在肩線，不是錨在一次試探性的基準動作）。
 *
 * 對長者而言這比個人化門檻更安全：舉手過肩本來就有解剖學標準，不該因人放寬。
 * `safetyNote` 已寫明「肩膀會痛就降低高度，不必舉到頂」，入門門檻 p ≥ 0.90
 * 代表舉到肩高的九成即算達標。
 *
 * ### 為什麼用「校正時的肩寬」而不是逐幀肩寬
 * 使用者若稍微側身，逐幀肩寬會縮小，比值就被放大、憑空多出達成率。
 * 固定用校正值與 M3 的設計一致（站立基準校正後即固定，確保 p 前後可比較）。
 */
class ArmRaiseSignal(
    /** 校正時「手腕 Y − 肩 Y」的平均值（像素，站姿時手腕在下方故為正）。 */
    private val restWristBelowShoulder: Float,
    /** 校正時的肩寬（像素）。 */
    private val shoulderWidth: Float,
) : RepSignal {
    // 分母改追蹤當下的肩寬（見 ScaleTracker）。分子本來就是兩個逐幀點的差，
    // 平移免疫；只有尺度是過期的，所以追蹤分母就讓整個訊號尺度不變。
    private val scale = ScaleTracker(shoulderWidth)

    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (shoulderWidth <= 0f) return null
        val shoulderY = averageY(points, KeyPointType.LEFT_SHOULDER, KeyPointType.RIGHT_SHOULDER)
            ?: return null
        val wristY = averageY(points, KeyPointType.LEFT_WRIST, KeyPointType.RIGHT_WRIST)
            ?: return null
        val currentWidth = scale.update(shoulderWidthOf(points) ?: 0f)
        if (currentWidth <= 0f) return null
        // 影像座標 Y 向下為正：手舉高時 wristY 變小，(wristY - shoulderY) 變小，進度變大。
        // 校正時的落差也要換算到當下尺度，否則兩項仍然不同單位。
        val restAtCurrentScale = restWristBelowShoulder * (currentWidth / shoulderWidth)
        return (restAtCurrentScale - (wristY - shoulderY)) / currentWidth
    }

    /** 手腕上舉到肩高（即 wristY == shoulderY）時的進度值。 */
    override val target: Float = restWristBelowShoulder / shoulderWidth

    // 三個門檻取 target 的比例而非絕對值：手臂動作的進度量級（約 1.5~2 個肩寬）
    // 與深蹲（約 0.3 個腿長）差一個數量級，沿用深蹲的絕對門檻會過度靈敏。
    override val enterThreshold = target * Config.ARM_RAISE_ENTER_FRACTION
    override val turnConfirmRise = target * Config.ARM_RAISE_TURN_CONFIRM_FRACTION
    override val returnThreshold = target * Config.ARM_RAISE_RETURN_FRACTION
}

/**
 * 擴胸推掌：進度 = 左右手腕的水平間距，以**校正時的肩寬**正規化。
 *
 * ### 為什麼量腕距，而不量「推掌」
 * 單一前視角相機看不到深度。**向前推掌時手往鏡頭方向伸，2D 投影幾乎不動**，
 * 根本量不到。可靠量到的只有「雙臂向兩側打開」那一半 —— 而那恰好就是擴胸本身。
 * 收回（推掌）是這一下的返回階段：雙手在身前併攏時腕距比站姿更小，
 * 進度掉到返回門檻以下，自然完成計次。
 *
 * ### 進度是「絕對腕距」，不是「相對站姿的增加量」
 * ⚠️ 原本進度取 `(腕距 − 站姿腕距)`，於是
 * `p = (峰值 − 站姿) / (判準 − 站姿)` —— **分母含有一個姿勢選擇**
 * （手垂下時離身體多遠）。站姿腕距離判準越近，分母越小，p 就被放大：
 * 2026-10-02 實機那場站姿腕距約 1.48 個肩寬，同樣「打開到最大側展 75%」的動作
 * 得到 p = 1.73，若站姿腕距是 0.90 則只得到 p = 1.41；在 55% 處更是一個 RED
 * 一個 YELLOW。**p 因此不可跨受試者比較**，對論文第四章是方法學問題。
 *
 * 改成絕對腕距後 `p = 峰值腕距 / 判準腕距`，完全不含站姿項，
 * p 就是「打開的幅度佔判準的幾成」，同一個幅度永遠得到同一個 p。
 *
 * **狀態機的行為完全不變** —— 三個門檻改為「以站姿為起點、往判準方向的比例」，
 * 換算回絕對腕距與改動前完全相同的像素值，所以已實機驗證過的計次不受影響。
 *
 * ### 判準錨在使用者自己的解剖尺寸上
 * 幾何最大側展腕距 = 肩寬 + 2 × 手臂長（雙臂完全側平舉時的腕距），兩個量都在站姿校正時量到。
 * [target] 取其 [Config.CHEST_EXPANSION_TARGET_FRACTION_OF_FULL]，所以 `p = 1.0`
 * 代表「打開到自己最大側展幅度的六成」。
 *
 * 副作用（好的）：寫進紀錄的 `duser` 現在只含解剖量，
 * 可反推 `手臂長/肩寬 = (duser / 0.6 − 1) / 2`，事後分析看得出校正品質。
 *
 * 與 [ArmRaiseSignal] 一樣用**校正時的**肩寬而非逐幀肩寬：使用者稍微側身時
 * 逐幀肩寬會縮小，比值被放大、憑空多出達成率。
 */
class ChestExpansionSignal(
    /** 校正時左右手腕的水平間距平均值（像素）。 */
    private val restSeparation: Float,
    /** 校正時的肩寬（像素）。 */
    private val shoulderWidth: Float,
    /** 校正時的手臂長，即站姿「手腕 Y − 肩 Y」（像素）。 */
    private val armLength: Float,
) : RepSignal {
    /** 站姿時的進度值。進度是絕對腕距，所以站姿**不是 0**，三個門檻都要以它為起點。 */
    private val restProgress = if (shoulderWidth > 0f) restSeparation / shoulderWidth else 0f

    /** 判準腕距（以肩寬正規化）：幾何最大側展的設定比例。只含解剖量，不含站姿項。 */
    override val target: Float =
        if (shoulderWidth > 0f) {
            (shoulderWidth + 2f * armLength) *
                Config.CHEST_EXPANSION_TARGET_FRACTION_OF_FULL / shoulderWidth
        } else {
            0f
        }

    /** 從站姿走到判準的距離。三個門檻都是它的比例，確保換算回像素與改動前一致。 */
    private val travel = target - restProgress

    // 與 ArmRaiseSignal 同一個問題：腕距是逐幀像素、分母是校正時像素。
    // 使用者往前站會讓腕距整體放大、進度憑空升高，站姿進度一旦高過返回門檻
    // 就永久卡在 UP（見 ScaleTracker）。
    private val scale = ScaleTracker(shoulderWidth)

    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (shoulderWidth <= 0f) return null
        val left = points[KeyPointType.LEFT_WRIST] ?: return null
        val right = points[KeyPointType.RIGHT_WRIST] ?: return null
        val currentWidth = scale.update(shoulderWidthOf(points) ?: 0f)
        if (currentWidth <= 0f) return null
        return kotlin.math.abs(left.x - right.x) / currentWidth
    }

    override val enterThreshold = restProgress + travel * Config.CHEST_EXPANSION_ENTER_FRACTION
    override val turnConfirmRise = travel * Config.CHEST_EXPANSION_TURN_CONFIRM_FRACTION
    override val returnThreshold = restProgress + travel * Config.CHEST_EXPANSION_RETURN_FRACTION

    /**
     * 站姿腕距必須確實小於判準腕距，否則 [travel] 會是 0 或負數、門檻全部錯亂。
     * 典型情形是使用者在站姿校正時就把手張開。
     */
    val isValid: Boolean get() = shoulderWidth > 0f && armLength > 0f && travel > 0f
}

/**
 * 原地高抬腿（單側）：進度 = 該側膝相對站姿的上抬量，以「髖-踝」垂直距離正規化。
 *
 * ### 左右腳各一個訊號、各一台狀態機
 * 「一下」的定義是**單腳抬一次**（使用者 2026-10-02 決定）。
 * 若用「較高的那隻膝」當單一訊號，兩腳的抬腿半波是**連續鋪滿**的 ——
 * 一腳落地的同時另一腳已經抬起，進度永遠湊不到連續
 * [Config.STAND_STABLE_FRAMES] 幀低於返回門檻，第一下之後就卡在 UP 出不來。
 * 模擬 25fps、每腳 0.8~2.0 秒的七種節奏，單一訊號有六種是 **0 下**
 * （唯一能動的是「每腳抬 40% 時間」那種雙腳落地有明顯空檔的節奏，
 * 完全取決於使用者節奏，太脆弱）。左右各一台則全部正確。
 *
 * ### 判準是「膝抬到髖高」
 * 大腿接近水平，是清楚的解剖學地標。[target] = 站姿「膝-髖」垂直距離 ÷ 腿長
 * ≈ 大腿長/腿長 ≈ 0.5，與其他動作的 target 同量級。
 *
 * 髖的基準與比例尺都取**站姿校正值**而非逐幀值：抬腿時骨盆會輕微晃動，
 * 用逐幀髖高當基準會讓同一個抬腿高度得到不同的進度。
 */
class HighKneeSignal(
    /** 這個訊號負責哪一側（[KeyPointType.LEFT_KNEE] 或 [KeyPointType.RIGHT_KNEE]）。 */
    private val knee: KeyPointType,
    /** 校正時該側膝的 Y（像素）。 */
    private val baselineKneeY: Float,
    /** 校正時髖部的 Y（像素）。 */
    private val baselineHipY: Float,
    /** 校正時「髖-踝」垂直距離（像素），作為身體比例尺。 */
    private val normalizeScale: Float,
) : RepSignal {
    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (normalizeScale <= 0f) return null
        val point = points[knee] ?: return null
        // 影像座標 Y 向下為正：膝抬高時 y 變小，進度變大。
        return (baselineKneeY - point.y) / normalizeScale
    }

    /** 膝抬到髖高（大腿水平）時的進度值。 */
    override val target: Float = (baselineKneeY - baselineHipY) / normalizeScale

    override val enterThreshold = target * Config.HIGH_KNEES_ENTER_FRACTION
    override val turnConfirmRise = target * Config.HIGH_KNEES_TURN_CONFIRM_FRACTION
    override val returnThreshold = target * Config.HIGH_KNEES_RETURN_FRACTION
}

/**
 * 站姿校正累加器：每個動作宣告自己要從站姿量什麼，量滿後產生對應的 [RepSignal]。
 *
 * 不能統一累加髖/踝/肩/腕四組 —— 品質檢查只保證「當前動作宣告需要的點」到齊，
 * 手臂動作時髖與踝可能根本沒偵測到，一起累加會讓站姿校正永遠跑不完。
 */
interface StandCalibrator {
    /** 餵入一幀站姿樣本。所需關鍵點缺漏時回傳 false，該幀不計入。 */
    fun accumulate(points: Map<KeyPointType, KeyPoint>): Boolean

    /** 已累積的有效樣本數。 */
    val sampleCount: Int

    /**
     * 產生動作訊號。失敗（樣本不足、量測無效）時回傳**空清單**。
     *
     * 回傳清單而非單一訊號，是因為左右交替的動作（原地高抬腿）必須左右各一個
     * 獨立訊號、各一台狀態機 —— 用單一訊號會讓兩次抬腿併成一次甚至完全計不到
     * （見 [HighKneeSignal] 的註解）。其餘動作回傳一個元素。
     *
     * [strict] 為 false 時跳過「解剖學合理性」檢查（如手臂長/肩寬是否在合理區間），
     * 但**不跳過**會產生垃圾判準的硬性檢查（如手臂長 ≤ 0）。
     * 呼叫端在重試次數用完後用它照收，避免使用者卡在校正出不去。
     */
    fun build(strict: Boolean = true): List<RepSignal>
}

/** 深蹲家族：量髖部站立基準高度與「髖-踝」垂直距離。 */
class LowerBodyStandCalibrator : StandCalibrator {
    private var hipSum = 0f
    private var ankleSum = 0f
    override var sampleCount = 0
        private set

    /** 站立基準髖部 Y，供膝內夾等其他模組沿用。 */
    val baselineY: Float? get() = if (sampleCount > 0) hipSum / sampleCount else null

    /** 髖-踝垂直距離，作為身體比例尺。 */
    val normalizeScale: Float?
        get() = baselineY?.let { ankleSum / sampleCount - it }

    override fun accumulate(points: Map<KeyPointType, KeyPoint>): Boolean {
        val hipY = averageY(points, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP) ?: return false
        val ankleY = averageY(points, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
            ?: return false
        hipSum += hipY
        ankleSum += ankleY
        sampleCount += 1
        return true
    }

    override fun build(strict: Boolean): List<RepSignal> {
        val baseline = baselineY ?: return emptyList()
        val scale = normalizeScale ?: return emptyList()
        if (scale <= 0f) return emptyList()
        return listOf(SquatSignal(baseline, scale))
    }
}

/** 雙臂高舉：量站姿時手腕低於肩的距離，以及肩寬。 */
class ArmRaiseStandCalibrator : StandCalibrator {
    private var dropSum = 0f
    private var widthSum = 0f
    override var sampleCount = 0
        private set

    override fun accumulate(points: Map<KeyPointType, KeyPoint>): Boolean {
        val left = points[KeyPointType.LEFT_SHOULDER] ?: return false
        val right = points[KeyPointType.RIGHT_SHOULDER] ?: return false
        val shoulderY = (left.y + right.y) / 2f
        val wristY = averageY(points, KeyPointType.LEFT_WRIST, KeyPointType.RIGHT_WRIST)
            ?: return false
        dropSum += wristY - shoulderY
        widthSum += kotlin.math.abs(left.x - right.x)
        sampleCount += 1
        return true
    }

    override fun build(strict: Boolean): List<RepSignal> {
        if (sampleCount == 0) return emptyList()
        val drop = dropSum / sampleCount
        val width = widthSum / sampleCount
        // 站姿時手腕必須確實低於肩：若使用者校正時就把手舉著，drop <= 0，
        // target 會是 0 或負數，之後每一下的 p 都會變成無意義的數字。
        if (drop <= 0f || width <= 0f) return emptyList()
        if (strict && !isPlausibleArmToShoulder(drop / width)) return emptyList()
        return listOf(ArmRaiseSignal(drop, width))
    }
}

/** 擴胸推掌：量站姿腕距、肩寬，以及手臂長（判準要用它算幾何最大側展幅度）。 */
class ChestExpansionStandCalibrator : StandCalibrator {
    private var separationSum = 0f
    private var widthSum = 0f
    private var armSum = 0f
    override var sampleCount = 0
        private set

    override fun accumulate(points: Map<KeyPointType, KeyPoint>): Boolean {
        val leftShoulder = points[KeyPointType.LEFT_SHOULDER] ?: return false
        val rightShoulder = points[KeyPointType.RIGHT_SHOULDER] ?: return false
        val leftWrist = points[KeyPointType.LEFT_WRIST] ?: return false
        val rightWrist = points[KeyPointType.RIGHT_WRIST] ?: return false
        separationSum += kotlin.math.abs(leftWrist.x - rightWrist.x)
        widthSum += kotlin.math.abs(leftShoulder.x - rightShoulder.x)
        armSum += (leftWrist.y + rightWrist.y) / 2f - (leftShoulder.y + rightShoulder.y) / 2f
        sampleCount += 1
        return true
    }

    override fun build(strict: Boolean): List<RepSignal> {
        if (sampleCount == 0) return emptyList()
        val separation = separationSum / sampleCount
        val width = widthSum / sampleCount
        val arm = armSum / sampleCount
        // 站姿時手腕必須確實低於肩（arm > 0）；若使用者校正時就把手張開或舉著，
        // 量到的站姿基準不可信，判準會變成 0 或負數，之後每一下的 p 都沒有意義。
        if (width <= 0f || arm <= 0f) return emptyList()
        if (strict && !isPlausibleArmToShoulder(arm / width)) return emptyList()
        val signal = ChestExpansionSignal(separation, width, arm)
        // 站姿腕距已達或超過判準腕距時（例如校正時就把手張開），門檻會錯亂，
        // 之後每一下的計次與 p 都沒有意義。
        if (!signal.isValid) return emptyList()
        return listOf(signal)
    }
}

/**
 * 原地高抬腿：量左右膝的站姿高度、髖部基準高度，以及腿長比例尺。
 *
 * 另外需要踝（算腿長），但踝**不在** `requiredPoints` 裡 ——
 * 抬腿時被抬起那腳的踝最容易掉信心值，列入必要點會讓動作峰值的幀被整幀丟掉。
 * 校正發生在靜止時，踝可靠；拿不到時這裡回傳 false 跳過該幀即可。
 */
class HighKneesStandCalibrator : StandCalibrator {
    private var hipSum = 0f
    private var ankleSum = 0f
    private var leftKneeSum = 0f
    private var rightKneeSum = 0f
    override var sampleCount = 0
        private set

    override fun accumulate(points: Map<KeyPointType, KeyPoint>): Boolean {
        val hipY = averageY(points, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP) ?: return false
        val ankleY = averageY(points, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
            ?: return false
        val leftKnee = points[KeyPointType.LEFT_KNEE] ?: return false
        val rightKnee = points[KeyPointType.RIGHT_KNEE] ?: return false
        hipSum += hipY
        ankleSum += ankleY
        leftKneeSum += leftKnee.y
        rightKneeSum += rightKnee.y
        sampleCount += 1
        return true
    }

    override fun build(strict: Boolean): List<RepSignal> {
        if (sampleCount == 0) return emptyList()
        val hipY = hipSum / sampleCount
        val scale = ankleSum / sampleCount - hipY
        if (scale <= 0f) return emptyList()
        val leftKneeY = leftKneeSum / sampleCount
        val rightKneeY = rightKneeSum / sampleCount
        // 站姿時膝必須確實低於髖，否則判準會是 0 或負數。
        if (leftKneeY <= hipY || rightKneeY <= hipY) return emptyList()
        return listOf(
            HighKneeSignal(KeyPointType.LEFT_KNEE, leftKneeY, hipY, scale),
            HighKneeSignal(KeyPointType.RIGHT_KNEE, rightKneeY, hipY, scale),
        )
    }
}

/**
 * 踮腳尖：進度 = 「髖相對腳尖的上升量」÷ 站姿腿長。
 *
 * ### 為什麼量髖，不量腳跟
 * 直覺上「腳跟相對腳尖的垂直落差」才是踮腳尖本身，而且是局部量測、不受身體晃動污染。
 * 兩份逐幀資料否決了這個直覺：**ML Kit 的腳部關鍵點低估垂直抬升 1.6~2.2 倍**。
 * 小腿是剛體，腳掌踩地以腳尖為軸抬起時，**膝的上升量必須等於踝的上升量**，
 * 實測膝卻是踝的兩倍以上 —— 解剖學上不可能，所以是關鍵點被模型先驗壓住，
 * 不是使用者踮得不夠高（髖的 0.094 個腿長 ≈ 8cm，是完整的提踵）。
 *
 * 腳跟訊號的振幅/雜訊只有 6~8；以髖為量測點是 25。
 * 腳尖仍然是**錨點**（它踩在地上不動），所以身體的平移會被抵銷。
 *
 * ### 為什麼基準來自倒數期間，不是站姿校正期間
 * 這是整個動作成立與否的關鍵。踮腳尖的振幅只有腿長的 6~8%，而使用者在
 * 站姿校正與訓練之間移動幾個百分點是常態 —— 實測一場退了 6%
 * （腿長 291.9 → 274.2px），用站姿校正的基準跑狀態機是 **0 下**，整場進度全為負。
 *
 * | 基準取樣時機 | 基準誤差／振幅（兩場） |
 * |---|---|
 * | 站姿校正 STAND_HOLD | 0.83 / **6.29** |
 * | 倒數 READY_COUNTDOWN | **0.19 / 0.35** |
 *
 * 倒數是使用者**正式開始前站定的最後一刻**，位置與訓練時一致。
 * 其餘五個動作的振幅大一個數量級（高抬腿是腿長的 50%），同樣的誤差無傷，
 * 所以它們維持用站姿校正的基準 —— 不動已經實機驗證過的東西。
 */
class HeelRaiseSignal(
    /** 倒數期間量到的「腳尖 Y − 髖 Y」平均值。 */
    private val baselineToeToHip: Float,
    /** 倒數期間量到的「踝 Y − 髖 Y」平均值（腿長）。 */
    private val normalizeScale: Float,
) : RepSignal {
    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (normalizeScale <= 0f) return null
        val toeY = averageY(points, KeyPointType.LEFT_TOE, KeyPointType.RIGHT_TOE) ?: return null
        val hipY = averageY(points, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP) ?: return null
        // 影像座標 Y 向下為正：踮起時髖上升（hipY 變小），(toeY - hipY) 變大，進度變正。
        return ((toeY - hipY) - baselineToeToHip) / normalizeScale
    }

    override val target: Float = Config.HEEL_RAISE_TARGET_RISE_RATIO
    override val enterThreshold: Float = target * Config.HEEL_RAISE_ENTER_FRACTION
    override val turnConfirmRise: Float = target * Config.HEEL_RAISE_TURN_CONFIRM_FRACTION
    override val returnThreshold: Float = target * Config.HEEL_RAISE_RETURN_FRACTION
}

/**
 * 踮腳尖的站姿／倒數量測累加器。
 *
 * 同一個類別被用在兩個階段：站姿校正（做合理性檢查、讓使用者看 guidance）
 * 與倒數期間（真正拿來當基準的那一次量測）。
 * [HEEL_RAISE] 之所以需要第二次，見 [HeelRaiseSignal] 的說明。
 */
class HeelRaiseStandCalibrator : StandCalibrator {
    private var toeToHipSum = 0f
    private var legSum = 0f
    override var sampleCount = 0
        private set

    override fun accumulate(points: Map<KeyPointType, KeyPoint>): Boolean {
        val hipY = averageY(points, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP) ?: return false
        val toeY = averageY(points, KeyPointType.LEFT_TOE, KeyPointType.RIGHT_TOE) ?: return false
        val ankleY = averageY(points, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
            ?: return false
        toeToHipSum += toeY - hipY
        legSum += ankleY - hipY
        sampleCount += 1
        return true
    }

    override fun build(strict: Boolean): List<RepSignal> {
        if (sampleCount == 0) return emptyList()
        val toeToHip = toeToHipSum / sampleCount
        val leg = legSum / sampleCount
        // 腳尖必須低於髖、踝必須低於髖，否則整個比值沒有意義。
        if (leg <= 0f || toeToHip <= 0f) return emptyList()
        // 腳尖理應比踝更低（透視使然），所以比值必定 > 1。校正時人就已經踮著、
        // 或關鍵點整組亂掉時這個比值會跑掉。
        if (strict && !isPlausibleToeToHip(toeToHip / leg)) return emptyList()
        return listOf(HeelRaiseSignal(toeToHip, leg))
    }
}

/**
 * 這組訊號需不需要在倒數期間重新量基準？
 *
 * 判據是**判準有多小**，不是動作是哪一個 —— 見
 * [Config.COUNTDOWN_REBASELINE_TARGET_MAX]。把分流綁在成因上而不是名稱上，
 * 是因為「依動作名稱分流」在這個專案裡已經壞過三次（框位引導換過三種判據）。
 *
 * 深蹲家族的 `target` 是 null（分母是 Duser），回傳 false：
 * 它們的基準誤差同樣存在，但振幅大一個數量級，而且已經實機驗證過 ——
 * 不動已驗證的東西。
 */
fun needsCountdownRebaseline(signals: List<RepSignal>): Boolean {
    val target = signals.firstOrNull()?.target ?: return false
    return target <= Config.COUNTDOWN_REBASELINE_TARGET_MAX
}

/** 站姿量到的「腳尖-髖 ÷ 腿長」是否合理。區間見 [Config.CALIBRATION_MIN_TOE_TO_HIP_RATIO]。 */
internal fun isPlausibleToeToHip(ratio: Float): Boolean =
    ratio in Config.CALIBRATION_MIN_TOE_TO_HIP_RATIO..Config.CALIBRATION_MAX_TOE_TO_HIP_RATIO

/**
 * 站姿量到的「手臂長 ÷ 肩寬」是否落在解剖學合理區間。
 *
 * 兩個上肢動作的判準都建立在手臂長上。手沒有完全自然下垂時手臂長被低估，
 * 判準跟著變小、**整場 p 都偏高、假性達標** —— 實測六場裡有兩場如此
 * （0.962 / 1.069，而正常的四場是 1.348~1.409）。
 * 區間見 [Config.CALIBRATION_MIN_ARM_TO_SHOULDER]。
 */
internal fun isPlausibleArmToShoulder(ratio: Float): Boolean =
    ratio in Config.CALIBRATION_MIN_ARM_TO_SHOULDER..Config.CALIBRATION_MAX_ARM_TO_SHOULDER

/**
 * 取得動作對應的站姿校正累加器。
 *
 * **尚未實作偵測的動作回傳 null，而不是借用深蹲的累加器。** 借用的話，哪天
 * `detectionImplemented` 被打開，動作會「看起來有在偵測」但判準完全是錯的 ——
 * 這正是 `CalibrationKind` 與 `exerciseType` 漏匯出那兩個 bug 的模式：
 * 宣告了、看起來有接上、實際上沒有。
 * `RepSignalTest` 有一條不變式測試鎖住「`detectionImplemented` ⇔ 有累加器」。
 */
fun standCalibratorFor(exercise: ExerciseType): StandCalibrator? = when (exercise) {
    ExerciseType.SQUAT, ExerciseType.CHAIR_SQUAT -> LowerBodyStandCalibrator()
    ExerciseType.ARM_RAISE -> ArmRaiseStandCalibrator()
    ExerciseType.CHEST_EXPANSION -> ChestExpansionStandCalibrator()
    ExerciseType.HEEL_RAISE -> HeelRaiseStandCalibrator()
    ExerciseType.HIGH_KNEES -> HighKneesStandCalibrator()
}

internal fun averageY(
    points: Map<KeyPointType, KeyPoint>,
    a: KeyPointType,
    b: KeyPointType,
): Float? {
    val pa = points[a] ?: return null
    val pb = points[b] ?: return null
    return (pa.y + pb.y) / 2f
}
