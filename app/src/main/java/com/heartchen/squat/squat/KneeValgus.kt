package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import kotlin.math.abs

/**
 * M4 膝內夾（knee valgus）判定：以髖寬正規化「踝距 - 膝距」，
 * 若膝蓋間距明顯小於腳踝間距，代表兩膝往內夾，視為膝內夾。
 * 僅設計在 BOTTOM 觸發瞬間呼叫一次，避免頻繁閃爍（見 CLAUDE.md 第 7 節）。
 * 六個關鍵點缺任何一個都無法判定，回傳 null。
 */
fun detectKneeValgus(keyPointsByType: Map<KeyPointType, KeyPoint>): Boolean? {
    val leftHip = keyPointsByType[KeyPointType.LEFT_HIP] ?: return null
    val rightHip = keyPointsByType[KeyPointType.RIGHT_HIP] ?: return null
    val leftKnee = keyPointsByType[KeyPointType.LEFT_KNEE] ?: return null
    val rightKnee = keyPointsByType[KeyPointType.RIGHT_KNEE] ?: return null
    val leftAnkle = keyPointsByType[KeyPointType.LEFT_ANKLE] ?: return null
    val rightAnkle = keyPointsByType[KeyPointType.RIGHT_ANKLE] ?: return null

    val hipWidth = abs(rightHip.x - leftHip.x)
    if (hipWidth <= 0f) return null

    val kneeDistance = abs(rightKnee.x - leftKnee.x)
    val ankleDistance = abs(rightAnkle.x - leftAnkle.x)

    val valgusRatio = (ankleDistance - kneeDistance) / hipWidth
    return valgusRatio > Config.KNEE_VALGUS_RATIO_THRESHOLD
}
