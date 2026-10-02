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
    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (shoulderWidth <= 0f) return null
        val shoulderY = averageY(points, KeyPointType.LEFT_SHOULDER, KeyPointType.RIGHT_SHOULDER)
            ?: return null
        val wristY = averageY(points, KeyPointType.LEFT_WRIST, KeyPointType.RIGHT_WRIST)
            ?: return null
        // 影像座標 Y 向下為正：手舉高時 wristY 變小，(wristY - shoulderY) 變小，進度變大。
        return (restWristBelowShoulder - (wristY - shoulderY)) / shoulderWidth
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
 * 擴胸推掌：進度 = 左右手腕的水平間距相對站姿的增加量，以**校正時的肩寬**正規化。
 *
 * ### 為什麼量腕距，而不量「推掌」
 * 單一前視角相機看不到深度。**向前推掌時手往鏡頭方向伸，2D 投影幾乎不動**，
 * 根本量不到。可靠量到的只有「雙臂向兩側打開」那一半 —— 而那恰好就是擴胸本身。
 * 收回（推掌）是這一下的返回階段：雙手在身前併攏時腕距比站姿更小，進度會變負，
 * 自然滿足返回條件。
 *
 * ### 判準錨在使用者自己的解剖尺寸上
 * 幾何最大間距 = 肩寬 + 2 × 手臂長（雙臂完全側平舉時的腕距），兩個量都在站姿校正時量到。
 * [target] 取其 [Config.CHEST_EXPANSION_TARGET_FRACTION_OF_FULL]，所以 `p = 1.0`
 * 代表「打開到自己最大側展幅度的六成」，而不是一個憑感覺訂的絕對距離。
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
    override fun progress(points: Map<KeyPointType, KeyPoint>): Float? {
        if (shoulderWidth <= 0f) return null
        val left = points[KeyPointType.LEFT_WRIST] ?: return null
        val right = points[KeyPointType.RIGHT_WRIST] ?: return null
        return (kotlin.math.abs(left.x - right.x) - restSeparation) / shoulderWidth
    }

    /** 腕距達到「幾何最大側展幅度 × 設定比例」時的進度值。 */
    override val target: Float =
        ((shoulderWidth + 2f * armLength) * Config.CHEST_EXPANSION_TARGET_FRACTION_OF_FULL -
            restSeparation) / shoulderWidth

    override val enterThreshold = target * Config.CHEST_EXPANSION_ENTER_FRACTION
    override val turnConfirmRise = target * Config.CHEST_EXPANSION_TURN_CONFIRM_FRACTION
    override val returnThreshold = target * Config.CHEST_EXPANSION_RETURN_FRACTION
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

    /** 產生動作訊號。樣本不足或量測無效（如肩寬為 0）時回傳 null。 */
    fun build(): RepSignal?
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

    override fun build(): RepSignal? {
        val baseline = baselineY ?: return null
        val scale = normalizeScale ?: return null
        if (scale <= 0f) return null
        return SquatSignal(baseline, scale)
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

    override fun build(): RepSignal? {
        if (sampleCount == 0) return null
        val drop = dropSum / sampleCount
        val width = widthSum / sampleCount
        // 站姿時手腕必須確實低於肩：若使用者校正時就把手舉著，drop <= 0，
        // target 會是 0 或負數，之後每一下的 p 都會變成無意義的數字。
        if (drop <= 0f || width <= 0f) return null
        return ArmRaiseSignal(drop, width)
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

    override fun build(): RepSignal? {
        if (sampleCount == 0) return null
        val separation = separationSum / sampleCount
        val width = widthSum / sampleCount
        val arm = armSum / sampleCount
        // 站姿時手腕必須確實低於肩（arm > 0）；若使用者校正時就把手張開或舉著，
        // 量到的站姿基準不可信，判準會變成 0 或負數，之後每一下的 p 都沒有意義。
        if (width <= 0f || arm <= 0f) return null
        val signal = ChestExpansionSignal(separation, width, arm)
        // 站姿腕距已經超過判準腕距時 target 會是 0 或負數（例如校正時就把手張開），
        // 那之後每一下的 p 都沒有意義。
        if (signal.target <= 0f) return null
        return signal
    }
}

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
    ExerciseType.HIGH_KNEES, ExerciseType.HEEL_RAISE -> null
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
