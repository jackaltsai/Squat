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

    val heightPx = frame.imageHeight.toFloat()
    if (heightPx <= 0f) return FramingIssue.OK

    val ankleY = averageY(byType, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
        ?: return missingAnkleAdvice(frame, heightPx)

    if (ankleY / heightPx > Config.FRAMING_ANKLE_NEAR_EDGE_RATIO) {
        return FramingIssue.MISSING_ANKLE
    }
    if (hipY / heightPx < Config.FRAMING_HIP_NEAR_TOP_RATIO) {
        return FramingIssue.HIP_NEAR_TOP_EDGE
    }

    val legSpanRatio = (ankleY - hipY) / heightPx
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
        ?: return missingWristAdvice(frame, heightPx)

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
 * 必要關鍵點**在畫面裡但信心值不足**時該給什麼建議。
 *
 * 這是兩種完全不同的狀況，而原本的程式碼把它們混成同一句話：
 *
 * | 原始座標在哪 | 真正的問題 | 該說 |
 * |---|---|---|
 * | 超出畫面邊緣 | 動作做得太大／站太近 | 舉到肩膀就好／請往後站 |
 * | **在畫面裡** | ML Kit 追不準，通常是人太小 | **請往前站** |
 *
 * 2026-10-05 實機：雙臂高舉站太遠，手腕信心值掉到門檻下，
 * 使用者得到的是「請讓雙手入鏡」—— 手明明在畫面裡，建議的方向還是錯的。
 *
 * 用**原始座標**（ML Kit 偵測到人就會給全部 33 點，不管看不看得到）分辨這兩種，
 * 不需要新的門檻 —— 邊緣判定沿用既有的
 * [Config.FRAMING_WRIST_NEAR_TOP_RATIO] / [Config.FRAMING_ANKLE_NEAR_EDGE_RATIO]。
 * 「站近一點」對信心值永遠是有效的動作，所以在畫面裡卻追不準時一律這樣說。
 */
private fun missingWristAdvice(frame: PoseFrame, heightPx: Float): FramingIssue {
    val rawWristY = averageY(
        frame.keyPoints.associateBy { it.type },
        KeyPointType.LEFT_WRIST,
        KeyPointType.RIGHT_WRIST
    ) ?: return FramingIssue.MISSING_WRIST   // 連座標都沒有 → 真的沒偵測到手
    return if (rawWristY / heightPx < Config.FRAMING_WRIST_NEAR_TOP_RATIO) {
        FramingIssue.WRIST_NEAR_TOP_EDGE
    } else {
        FramingIssue.TOO_FAR
    }
}

/** 腳踝版本，見 [missingWristAdvice]。腳踝的邊緣在**下方**，超出代表站太近。 */
private fun missingAnkleAdvice(frame: PoseFrame, heightPx: Float): FramingIssue {
    val rawAnkleY = averageY(
        frame.keyPoints.associateBy { it.type },
        KeyPointType.LEFT_ANKLE,
        KeyPointType.RIGHT_ANKLE
    ) ?: return FramingIssue.MISSING_ANKLE
    return if (rawAnkleY / heightPx > Config.FRAMING_ANKLE_NEAR_EDGE_RATIO) {
        FramingIssue.MISSING_ANKLE          // 腳踝被下緣裁掉 → 請往後站
    } else {
        FramingIssue.TOO_FAR
    }
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
