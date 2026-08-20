package com.heartchen.squat.pose

import com.heartchen.squat.config.Config

/**
 * M2 品質過濾：六個必要關鍵點（左右髖/膝/踝）須全部偵測到，且信心值都不低於門檻，
 * 這一幀才視為可信賴、可餵給 EMA 平滑與狀態機使用；否則整幀捨棄。
 */
fun PoseFrame.passesQualityCheck(threshold: Float = Config.CONFIDENCE_THRESHOLD): Boolean {
    if (keyPoints.size < KeyPointType.entries.size) return false
    return keyPoints.all { it.inFrameLikelihood >= threshold }
}
