package com.heartchen.squat.pose

import com.heartchen.squat.config.Config
import com.heartchen.squat.squat.ExerciseType

/**
 * 依偵測到的關鍵點位置，判斷目前手機擺放框位是否理想，並給出文字引導。
 * 手機鏡頭無法自己移動，這裡只做「偵測到框位不佳就用文字提示使用者手動調整」，
 * 不做數位變焦或裁切等自動校正。
 *
 * **判準依動作而異。** 原本整個函式硬性要求腳踝，拿不到就回「請往後站一點」——
 * 對手臂動作而言腳踝既拿不到也不需要，使用者會被一直叫往後站。
 *
 * ⚠️ 上肢動作**不要求頭頂留得下整隻舉直的手**。曾經加過這條檢查，結果是
 * 「請往後站」與「請往前站」互相拉扯、距離完全拿不準（詳見
 * `Config.FRAMING_WRIST_NEAR_TOP_RATIO` 的註解）。判準是舉到肩高，
 * 追蹤到肩高就夠了。
 *
 * 訊息刻意都在六個字以內，而且主詞是「使用者自己」而非手機：
 * 這些字同時會被 TTS 念出來，而使用者正站在兩公尺外做動作 ——
 * 長句既來不及看完也來不及聽完，且他當下根本碰不到手機，
 * 叫他「把手機往後移」是一個他無法執行的指示。
 */
enum class FramingIssue(val message: String) {
    NO_POSE("請站到鏡頭前"),
    MISSING_ANKLE("請往後站一點"),
    TOO_CLOSE("請往後站"),
    TOO_FAR("請往前站"),
    // 髖部太靠近畫面上緣，通常是人站太近造成的，對使用者而言一樣是「往後站」
    HIP_NEAR_TOP_EDGE("請往後站"),
    /** 上肢動作：偵測不到手腕，通常是手垂在畫面外或被身體遮住。 */
    MISSING_WRIST("請讓雙手入鏡"),
    /**
     * 上肢動作：手腕已貼齊畫面上緣。
     *
     * 訊息是「舉到肩膀就好」而不是「請往後站」—— 判準本來就是舉到肩高，
     * 而且使用者正在動作中，叫他停下來走過去重新站位不如直接告訴他舉到哪裡就夠。
     */
    WRIST_NEAR_TOP_EDGE("舉到肩膀就好"),

    /**
     * 狀態機卡住太久 —— 不是框位問題，但共用同一條「告訴使用者該怎麼辦」的管線
     * （顯示 + 語音），所以放在這個 enum 裡。
     *
     * 2026-10-05 實機：雙臂高舉站到校正距離的 1.25 倍以上，手垂下時的進度
     * 再也低不過返回門檻，狀態機**永久停在 UP** —— 一下都不計。
     * 而 `atRest` 是「所有狀態機都在 STAND」，於是框位一律當成 OK、
     * **一片安靜**：使用者舉了 24 秒毫無反應，也沒有任何提示。
     *
     * 根因已修（見 `ScaleTracker`），但這道安全網要留著：
     * 下肢動作的基準是校正時的**絕對座標**，同類漂移還在，
     * 而不管什麼原因卡住，**沉默都不該是失敗模式**。
     */
    STUCK("請站回原來位置"),
    OK("框位良好")
}

/**
 * [exercise] 決定要用哪一組判準。預設值為深蹲，讓既有呼叫端與測試不必全部改寫，
 * 但實際呼叫時應一律傳入當前動作。
 */
fun evaluateFraming(
    frame: PoseFrame?,
    exercise: ExerciseType = ExerciseType.SQUAT
): FramingIssue {
    if (frame == null) return FramingIssue.NO_POSE
    // 只採信通過信心值門檻的點。ML Kit 偵測到人就會回傳**全部**關鍵點，
    // 不管看不看得到 —— 不過濾的話 `byType` 永遠是滿的，
    // MISSING_WRIST / MISSING_ANKLE / NO_POSE 幾乎永遠不會觸發，
    // 而後面的位置判斷會拿低信心值的（可能是亂猜的）座標去算。
    val byType = frame.keyPoints
        .filter { it.inFrameLikelihood >= Config.CONFIDENCE_THRESHOLD }
        .associateBy { it.type }
    // 分流依據是「這個動作要不要看手腕」，綁在集合的**內容**而非身分上。
    //
    // 曾經寫成 `requiredPoints == KeyPointType.UPPER_BODY` 的集合相等比較，
    // 動作一旦改了宣告的點就整個掉回下肢分支、「請往後站一點」全部回來。
    // 後來改成問「需不需要腳踝」，但原地高抬腿為了不讓抬起那腳的踝拖累品質檢查，
    // 刻意**不**宣告踝 —— 那樣問會把它誤判成上肢動作、去檢查手腕。
    // 問「要不要看手腕」對六個動作都成立，`FramingGuidanceTest` 有逐一驗證。
    return if (KeyPointType.LEFT_WRIST in exercise.requiredPoints) {
        evaluateUpperBodyFraming(byType, frame)
    } else {
        evaluateLowerBodyFraming(byType, frame, exercise)
    }
}

private fun evaluateLowerBodyFraming(
    byType: Map<KeyPointType, KeyPoint>,
    frame: PoseFrame,
    exercise: ExerciseType
): FramingIssue {
    val hipY = averageY(byType, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP)
        ?: return FramingIssue.NO_POSE
    // 腳踝不可信時維持原本的 MISSING_ANKLE（訊息是「請往後站一點」）。
    // 手腕那條路改了方向（見 missingWristAdvice），但下肢**沒有任何實測證據**
    // 說它有問題，而深蹲家族已經驗證數週 —— 不動已驗證的東西。
    // 下肢的主因也不同：腳踝不可信通常是被下緣裁掉（站太近），方向正好相反。
    // ⚠️ 腳踝不可信時，**只有這個動作真的宣告需要腳踝，才算是問題。**
    //
    // 原地高抬腿與踮腳尖刻意**不**宣告腳踝（抬起那腳的踝必然掉信心值，宣告了
    // 會讓那些幀被品質檢查整幀丟掉，而且正好丟在動作峰值上）。
    // 規則是：框位檢查可以**讀**任何關鍵點，但只能對**動作宣告需要**的那些報
    // MISSING；不需要的點拿不到時，就跳過依賴它的檢查，不要瞎猜。
    val ankleY = averageY(byType, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
    if (ankleY == null && KeyPointType.LEFT_ANKLE in exercise.requiredPoints) {
        return FramingIssue.MISSING_ANKLE
    }

    val heightPx = frame.imageHeight.toFloat()
    if (heightPx <= 0f) return FramingIssue.OK

    // 「快被下緣裁掉」只有在腳踝真的可信時才判斷。下面的換算值沒有這個語意 ——
    // 腳跟/腳尖/膝在哪裡並不代表腳踝在哪裡，拿推估值去比畫面邊緣是瞎猜。
    if (ankleY != null && ankleY / heightPx > Config.FRAMING_ANKLE_NEAR_EDGE_RATIO) {
        return FramingIssue.MISSING_ANKLE
    }
    if (hipY / heightPx < Config.FRAMING_HIP_NEAR_TOP_RATIO) {
        return FramingIssue.HIP_NEAR_TOP_EDGE
    }

    // 腳踝不可信 → 從別的可信下肢點換算回腿長，而不是乾脆不判斷。
    //
    // 不宣告腳踝的那兩個動作（原地高抬腿、踮腳尖）原本在這裡一律回 OK，
    // 也就是**整個遠近判斷消失、一片安靜**。使用者的回報正是
    // 「原地高抬腿及踮腳尖 偵測人物距離 可否修正正確一點」——
    // 跳過依賴不可信點的檢查是對的，但「跳過」不等於「無話可說」：
    // 髖→腳跟、髖→腳尖、髖→膝都量得到，而且和髖→踝是固定比例（見 Config）。
    //
    // 2026-10-05 的逐幀資料證明這兩種情況**必須分開**，而換算值分得開：
    //   • 10-02 錄影訓練中腳踝不可信的 13 幀：原始踝 y 沒有一幀出界，
    //     膝換算得到 0.494~0.578 → **OK**（腳只是離地，站位正確，就該安靜）
    //   • 10-05 錄影訓練中腳踝不可信的 29 幀：52% 原始踝 y 已過畫面下緣，
    //     髖→膝從靜止的 118px 漲到 206~246px，膝換算得到 0.613~0.739
    //     → **請往後站**（使用者是真的走近了，不是抬腳）
    //
    // ⚠️ 更正：這段註解原本寫「10-05 有 39 連續幀誤報、而他站的位置完全正確，
    // 腳踝只是剛剛離地過」，並以此為理由改成回 OK。逐幀資料不支持那個說法 ——
    // 訓練中最長的 MISSING_ANKLE 連續段是 18 幀（不是 39），而那幾段的
    // 髖→膝距離是靜止值的兩倍、原始踝座標已在畫面外：使用者確實站太近了，
    // **當時的「請往後站一點」是對的**。我拿一個讀錯的結論換掉了正確的提示。
    val legSpan = if (ankleY != null) {
        ankleY - hipY
    } else {
        // 髖以下一個可信的點都沒有：這裡不猜。維持 OK 與今天的行為一致，
        // 真的卡住會由 FramingIssue.STUCK 那道安全網接手。
        reconstructLegSpan(byType, hipY) ?: return FramingIssue.OK
    }

    val legSpanRatio = legSpan / heightPx
    // 踮腳尖的位移只有腿長的 6~8%，訊噪比對拍攝距離**極度敏感** ——
    // 實測腿長 226px 時只有 7.1、277px 時是 25.0，而 0.20 的一般門檻
    // 完全擋不住前者（226/640 = 0.35 照樣通過）。所以它要一個更嚴的下限。
    // 其餘下肢動作的位移大一個數量級，不需要被這個門檻綁住。
    val tooFarRatio = if (exercise == ExerciseType.HEEL_RAISE) {
        Config.FRAMING_HEEL_RAISE_MIN_LEG_SPAN_RATIO
    } else {
        Config.FRAMING_TOO_FAR_RATIO
    }
    return when {
        legSpanRatio < tooFarRatio -> FramingIssue.TOO_FAR
        legSpanRatio > Config.FRAMING_TOO_CLOSE_RATIO -> FramingIssue.TOO_CLOSE
        else -> FramingIssue.OK
    }
}

/**
 * 上肢動作的框位：肩與腕要在框內、遠近用肩寬佔畫面寬度判斷。
 *
 * 檢查順序是刻意的 —— 先回報「看不到」再回報「位置不對」，
 * 因為看不到手的時候沒辦法判斷遠近，先叫使用者往前站只會把事情弄得更亂。
 */
private fun evaluateUpperBodyFraming(
    byType: Map<KeyPointType, KeyPoint>,
    frame: PoseFrame
): FramingIssue {
    val leftShoulder = byType[KeyPointType.LEFT_SHOULDER] ?: return FramingIssue.NO_POSE
    val rightShoulder = byType[KeyPointType.RIGHT_SHOULDER] ?: return FramingIssue.NO_POSE

    val heightPx = frame.imageHeight.toFloat()
    val widthPx = frame.imageWidth.toFloat()
    if (heightPx <= 0f || widthPx <= 0f) return FramingIssue.OK

    val wristY = averageY(byType, KeyPointType.LEFT_WRIST, KeyPointType.RIGHT_WRIST)
        ?: return missingWristAdvice(frame)

    if (wristY / heightPx < Config.FRAMING_WRIST_NEAR_TOP_RATIO) {
        return FramingIssue.WRIST_NEAR_TOP_EDGE
    }

    val shoulderWidthRatio = kotlin.math.abs(leftShoulder.x - rightShoulder.x) / widthPx
    return when {
        shoulderWidthRatio < Config.FRAMING_SHOULDER_WIDTH_TOO_FAR_RATIO -> FramingIssue.TOO_FAR
        shoulderWidthRatio > Config.FRAMING_SHOULDER_WIDTH_TOO_CLOSE_RATIO -> FramingIssue.TOO_CLOSE
        else -> FramingIssue.OK
    }
}

/**
 * 手腕**存在但信心值不足**時該給什麼建議。
 *
 * 2026-10-05 實機：雙臂高舉站太遠，手腕信心值掉到門檻下，使用者得到
 * 「請讓雙手入鏡」—— 手明明在畫面裡，建議的方向還是錯的。正確動作是往前站。
 *
 * ⚠️ 我第一版是**讀原始座標**來分辨「舉出畫面上緣」與「站太遠」。
 * `FramingGuidanceTest` 的「低信心值的關鍵點不列入框位判斷」立刻把它打下來，
 * 而且打得對：**低信心值的座標就是亂猜的**，拿它當判斷依據不成立 ——
 * 2026-10-01 整場只聽得到「舉到肩膀就好」就是這麼來的。
 *
 * 所以這裡**只看點存不存在**，不看它在哪：
 *
 * | 狀況 | 說 |
 * |---|---|
 * | 有手腕關鍵點但信心值不足 | **請往前站**（站近一點是唯一對信心值有效的動作） |
 * | 連手腕關鍵點都沒有 | 請讓雙手入鏡 |
 *
 * 「舉出畫面上緣」仍然由 [FramingIssue.WRIST_NEAR_TOP_EDGE] 負責 ——
 * 走的是**信心值足夠**那條路，座標可信才拿來比對邊緣。
 */
private fun missingWristAdvice(frame: PoseFrame): FramingIssue {
    val raw = frame.keyPoints.associateBy { it.type }
    val hasWrist = KeyPointType.LEFT_WRIST in raw || KeyPointType.RIGHT_WRIST in raw
    return if (hasWrist) FramingIssue.TOO_FAR else FramingIssue.MISSING_WRIST
}

/**
 * 腿長（髖→踝）的替代來源，依**誤差放大倍率**由小到大排列。
 *
 * 換算是「量到的距離 ÷ 係數」，所以係數小於 1 的來源會把誤差放大：
 * 膝的係數 0.525 等於把任何追蹤誤差放大 1.9 倍，而腳跟的 1.05、腳尖的 1.18
 * 反而會縮小。實測 95 百分位誤差（佔畫面高度）正好照這個順序：
 * 腳跟 0.0032、腳尖 0.012~0.014、膝 0.018~0.104。所以腳跟優先、膝墊底。
 */
private val LEG_SPAN_FALLBACKS = listOf(
    Triple(KeyPointType.LEFT_HEEL, KeyPointType.RIGHT_HEEL, Config.LEG_SPAN_FROM_HEEL),
    Triple(KeyPointType.LEFT_TOE, KeyPointType.RIGHT_TOE, Config.LEG_SPAN_FROM_TOE),
    Triple(KeyPointType.LEFT_KNEE, KeyPointType.RIGHT_KNEE, Config.LEG_SPAN_FROM_KNEE),
)

/** 腳踝不可信時，用第一個可信的替代點換算回腿長；全都拿不到回 null。 */
private fun reconstructLegSpan(
    byType: Map<KeyPointType, KeyPoint>,
    hipY: Float
): Float? {
    for ((left, right, divisor) in LEG_SPAN_FALLBACKS) {
        val y = averageY(byType, left, right) ?: continue
        val span = (y - hipY) / divisor
        // 替代點跑到髖以上（例如膝抬得比髖還高）換算出來是負值或零，
        // 那不是腿長，往下一個來源找。
        if (span > 0f) return span
    }
    return null
}

private fun averageY(
    keyPointsByType: Map<KeyPointType, KeyPoint>,
    a: KeyPointType,
    b: KeyPointType
): Float? {
    val pa = keyPointsByType[a] ?: return null
    val pb = keyPointsByType[b] ?: return null
    return (pa.y + pb.y) / 2f
}
