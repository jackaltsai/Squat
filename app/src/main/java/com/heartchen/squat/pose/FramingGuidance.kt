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
 * 反過來，上肢動作有一個下肢動作沒有的要求：**頭頂必須留空間**，
 * 否則手舉過頭時手腕出框，而原本的框位引導對此一句話都不會說。
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
    /** 上肢動作：頭頂空間不足或手腕已貼上緣，舉起來會被裁掉。 */
    NO_HEADROOM("請往後站"),
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
    val byType = frame.keyPoints.associateBy { it.type }
    return if (exercise.requiredPoints == KeyPointType.UPPER_BODY) {
        evaluateUpperBodyFraming(byType, frame)
    } else {
        evaluateLowerBodyFraming(byType, frame)
    }
}

private fun evaluateLowerBodyFraming(
    byType: Map<KeyPointType, KeyPoint>,
    frame: PoseFrame
): FramingIssue {
    val hipY = averageY(byType, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP)
        ?: return FramingIssue.NO_POSE
    val ankleY = averageY(byType, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
        ?: return FramingIssue.MISSING_ANKLE

    val heightPx = frame.imageHeight.toFloat()
    if (heightPx <= 0f) return FramingIssue.OK

    if (ankleY / heightPx > Config.FRAMING_ANKLE_NEAR_EDGE_RATIO) {
        return FramingIssue.MISSING_ANKLE
    }
    if (hipY / heightPx < Config.FRAMING_HIP_NEAR_TOP_RATIO) {
        return FramingIssue.HIP_NEAR_TOP_EDGE
    }

    val legSpanRatio = (ankleY - hipY) / heightPx
    return when {
        legSpanRatio < Config.FRAMING_TOO_FAR_RATIO -> FramingIssue.TOO_FAR
        legSpanRatio > Config.FRAMING_TOO_CLOSE_RATIO -> FramingIssue.TOO_CLOSE
        else -> FramingIssue.OK
    }
}

/**
 * 上肢動作的框位：肩與腕要在框內、肩膀上方要留得下舉起的手、遠近用肩寬判斷。
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
    val wristY = averageY(byType, KeyPointType.LEFT_WRIST, KeyPointType.RIGHT_WRIST)
        ?: return FramingIssue.MISSING_WRIST

    val heightPx = frame.imageHeight.toFloat()
    val widthPx = frame.imageWidth.toFloat()
    if (heightPx <= 0f || widthPx <= 0f) return FramingIssue.OK

    val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
    // 手腕已經貼上緣，或肩膀上方空間不足以容納舉起的手
    if (wristY / heightPx < Config.FRAMING_WRIST_NEAR_TOP_RATIO) return FramingIssue.NO_HEADROOM
    if (shoulderY / heightPx < Config.FRAMING_SHOULDER_HEADROOM_RATIO) return FramingIssue.NO_HEADROOM

    val shoulderWidthRatio = kotlin.math.abs(leftShoulder.x - rightShoulder.x) / widthPx
    return when {
        shoulderWidthRatio < Config.FRAMING_SHOULDER_WIDTH_TOO_FAR_RATIO -> FramingIssue.TOO_FAR
        shoulderWidthRatio > Config.FRAMING_SHOULDER_WIDTH_TOO_CLOSE_RATIO -> FramingIssue.TOO_CLOSE
        else -> FramingIssue.OK
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
