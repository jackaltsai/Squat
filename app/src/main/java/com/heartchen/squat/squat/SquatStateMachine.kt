package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import kotlin.math.abs

/**
 * 深蹲計次狀態機：STAND → DOWN → BOTTOM → UP → STAND（計次 +1）。
 *
 * 最低點（BOTTOM）判定依據「髖部下降轉上升的轉折點 + 連續幀確認」，
 * 而非單一高度閾值，避免瞬間雜訊誤判（見 CLAUDE.md 第 7 節）。
 *
 * 站立基準高度與「髖-踝」正規化尺度在 STAND 狀態下持續緩慢自適應校正，
 * 尚未串接 M3 的正式站姿校正流程，僅供 M2 計次邏輯使用。
 */
class SquatStateMachine {
    var state: SquatState = SquatState.STAND
        private set
    var repCount: Int = 0
        private set

    private var standBaselineY: Float? = null
    private var normalizeScale: Float? = null
    private var prevHipY: Float? = null
    private var risingFrameCount = 0
    private var standStableFrameCount = 0

    /**
     * 餵入一幀「已通過品質過濾與 EMA 平滑」的關鍵點，回傳更新後的狀態。
     * [keyPointsByType] 必須包含左右髖與左右踝，缺少時本幀不更新狀態。
     */
    fun update(keyPointsByType: Map<KeyPointType, KeyPoint>): SquatState {
        val hipY = averageY(keyPointsByType, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP)
        val ankleY = averageY(keyPointsByType, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
        if (hipY == null || ankleY == null) return state

        if (state == SquatState.STAND) {
            adaptBaseline(hipY, ankleY)
        }

        val baseline = standBaselineY
        val scale = normalizeScale
        if (baseline == null || scale == null || scale <= 0f) {
            prevHipY = hipY
            return state
        }

        // depthRatio 正值代表髖部低於站立基準（往下蹲），以髖-踝距離正規化避免受拍攝距離影響。
        val depthRatio = (hipY - baseline) / scale

        when (state) {
            SquatState.STAND -> {
                if (depthRatio > Config.DOWN_ENTER_RATIO) {
                    state = SquatState.DOWN
                    risingFrameCount = 0
                }
            }

            SquatState.DOWN -> {
                val prev = prevHipY
                risingFrameCount = if (prev != null && hipY < prev) risingFrameCount + 1 else 0
                if (risingFrameCount >= Config.TURN_CONFIRM_FRAMES) {
                    state = SquatState.BOTTOM
                }
            }

            SquatState.BOTTOM -> {
                // BOTTOM 僅代表最低點判定瞬間，姿勢判定（M4）掛在此處觸發一次後立即進入 UP。
                state = SquatState.UP
                standStableFrameCount = 0
            }

            SquatState.UP -> {
                standStableFrameCount = if (depthRatio < Config.STAND_RETURN_RATIO) {
                    standStableFrameCount + 1
                } else {
                    0
                }
                if (standStableFrameCount >= Config.STAND_STABLE_FRAMES) {
                    state = SquatState.STAND
                    repCount++
                }
            }
        }

        prevHipY = hipY
        return state
    }

    private fun adaptBaseline(hipY: Float, ankleY: Float) {
        val currentBaseline = standBaselineY
        val currentScale = normalizeScale
        if (currentBaseline == null || currentScale == null) {
            standBaselineY = hipY
            normalizeScale = ankleY - hipY
            return
        }
        val delta = abs(hipY - currentBaseline)
        if (currentScale > 0f && delta / currentScale < Config.BASELINE_STABLE_RATIO) {
            val a = Config.BASELINE_ADAPT_ALPHA
            standBaselineY = a * hipY + (1 - a) * currentBaseline
            normalizeScale = a * (ankleY - hipY) + (1 - a) * currentScale
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
}
