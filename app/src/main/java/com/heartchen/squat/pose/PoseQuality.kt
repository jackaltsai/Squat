package com.heartchen.squat.pose

import com.heartchen.squat.config.Config

/**
 * M2 品質過濾：**當前動作需要的**關鍵點須全部偵測到，且信心值都不低於門檻，
 * 這一幀才視為可信賴、可餵給 EMA 平滑與狀態機使用；否則整幀捨棄。
 *
 * [required] 由動作自己宣告（見 `ExerciseType.requiredPoints`），而不是檢查
 * `KeyPointType.entries` 全部 —— 深蹲用不到手腕，手臂動作用不到腳踝，
 * 要求全部到齊只會讓可用的幀被大量丟棄。
 *
 * 只檢查 [required] 裡的點，其餘點就算缺漏或信心值低也不影響判定。
 */
fun PoseFrame.passesQualityCheck(
    required: Set<KeyPointType>,
    threshold: Float = Config.CONFIDENCE_THRESHOLD
): Boolean {
    if (required.isEmpty()) return false
    val byType = keyPoints.associateBy { it.type }
    return required.all { type ->
        val point = byType[type] ?: return false
        point.inFrameLikelihood >= threshold
    }
}
