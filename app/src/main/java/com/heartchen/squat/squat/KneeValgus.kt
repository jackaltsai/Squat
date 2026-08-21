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
 *
 * 這個公式假設鏡頭大致正面拍攝：側身時左右髖幾乎重疊、髖寬趨近 0，
 * 拿極小的髖寬當分母會讓比例被異常放大、誤判為膝內夾，
 * 因此用「髖寬 / 腿長」這個跟拍攝距離無關的比例，過濾掉髖寬不可靠（例如側身）的幀。
 */
fun kneeValgusRatio(keyPointsByType: Map<KeyPointType, KeyPoint>): Float? {
    val leftHip = keyPointsByType[KeyPointType.LEFT_HIP] ?: return null
    val rightHip = keyPointsByType[KeyPointType.RIGHT_HIP] ?: return null
    val leftKnee = keyPointsByType[KeyPointType.LEFT_KNEE] ?: return null
    val rightKnee = keyPointsByType[KeyPointType.RIGHT_KNEE] ?: return null
    val leftAnkle = keyPointsByType[KeyPointType.LEFT_ANKLE] ?: return null
    val rightAnkle = keyPointsByType[KeyPointType.RIGHT_ANKLE] ?: return null

    val hipWidth = abs(rightHip.x - leftHip.x)
    val hipY = (leftHip.y + rightHip.y) / 2f
    val ankleY = (leftAnkle.y + rightAnkle.y) / 2f
    val legLength = abs(ankleY - hipY)
    if (legLength <= 0f) return null
    if (hipWidth / legLength < Config.KNEE_VALGUS_MIN_HIP_WIDTH_TO_LEG_RATIO) return null

    val kneeDistance = abs(rightKnee.x - leftKnee.x)
    val ankleDistance = abs(rightAnkle.x - leftAnkle.x)

    return (ankleDistance - kneeDistance) / hipWidth
}

fun detectKneeValgus(keyPointsByType: Map<KeyPointType, KeyPoint>): Boolean? {
    val ratio = kneeValgusRatio(keyPointsByType) ?: return null
    return ratio > Config.KNEE_VALGUS_RATIO_THRESHOLD
}
