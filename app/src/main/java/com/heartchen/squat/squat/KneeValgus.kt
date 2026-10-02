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
/**
 * 這個動作該不該做膝內夾判定。
 *
 * 公式是「(踝距 − 膝距) ÷ 髖寬」，**只對雙腳站地的下肢動作有意義** ——
 * 兩膝與兩踝都在地面上才能互相比較。判據取「同時宣告需要兩膝與兩踝」：
 *
 * | 動作 | 判定 | 原因 |
 * |---|---|---|
 * | 深蹲、坐站、踮腳尖 | ✅ | 雙腳站地，兩膝兩踝可比 |
 * | 原地高抬腿 | ✗ | 一腳離地，公式失去意義（刻意不宣告踝） |
 * | 雙臂高舉、擴胸推掌 | ✗ | 根本不看下肢 |
 *
 * ⚠️ 不擋的話會寫入**垃圾數值**而非 null —— 2026-10-02 實機的高抬腿紀錄
 * `kneeValgusRatio` 落在 −0.26 ~ −0.55，看起來像「量到了而且沒內夾」，
 * 實際上那個數字毫無意義。M5 做門檻掃描時若把這些列入，會污染驗證集。
 */
fun judgesKneeValgus(exercise: ExerciseType): Boolean =
    KeyPointType.LEFT_KNEE in exercise.requiredPoints &&
        KeyPointType.RIGHT_KNEE in exercise.requiredPoints &&
        KeyPointType.LEFT_ANKLE in exercise.requiredPoints &&
        KeyPointType.RIGHT_ANKLE in exercise.requiredPoints

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
