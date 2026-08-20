package com.heartchen.squat.pose

import com.heartchen.squat.config.Config

/**
 * 依偵測到的髖/踝關鍵點位置，判斷目前手機擺放框位是否理想，並給出文字引導。
 * 手機鏡頭無法自己移動，這裡只做「偵測到框位不佳就用文字提示使用者手動調整」，
 * 不做數位變焦或裁切等自動校正。
 */
enum class FramingIssue(val message: String) {
    NO_POSE("偵測不到人，請站到鏡頭前"),
    MISSING_ANKLE("偵測不到腳踝，請將手機放低或往後退，確保全身入鏡"),
    TOO_CLOSE("距離太近，請將手機往後退一點"),
    TOO_FAR("距離太遠，請將手機往前移一點"),
    HIP_NEAR_TOP_EDGE("上半身可能被裁到畫面外，請將手機放低一點"),
    OK("框位良好")
}

fun evaluateFraming(frame: PoseFrame?): FramingIssue {
    if (frame == null) return FramingIssue.NO_POSE

    val byType = frame.keyPoints.associateBy { it.type }
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

private fun averageY(
    keyPointsByType: Map<KeyPointType, KeyPoint>,
    a: KeyPointType,
    b: KeyPointType
): Float? {
    val pa = keyPointsByType[a] ?: return null
    val pb = keyPointsByType[b] ?: return null
    return (pa.y + pb.y) / 2f
}
