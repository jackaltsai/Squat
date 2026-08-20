package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType

/**
 * 深蹲計次狀態機：STAND → DOWN → BOTTOM → UP → STAND（計次 +1）。
 *
 * 最低點（BOTTOM）判定依據「髖部下降轉上升的轉折點 + 連續幀確認」，
 * 而非單一高度閾值，避免瞬間雜訊誤判（見 CLAUDE.md 第 7 節）。
 *
 * [standBaselineY] / [normalizeScale]（髖-踝垂直距離）來自 M3 的正式校正流程
 * （見 `CalibrationPhase`），此後在整個訓練過程中固定不變，不再像 M2 那樣持續自適應，
 * 確保後續深度達成率 p = Dnow / Duser 的計算基準前後一致。
 */
class SquatStateMachine(
    private val standBaselineY: Float,
    private val normalizeScale: Float
) {
    var state: SquatState = SquatState.STAND
        private set
    var repCount: Int = 0
        private set

    /** 最近一次 BOTTOM 觸發時的深度達成比例（相對站立基準、以髖-踝距離正規化），BOTTOM 觸發後才有值。 */
    var lastBottomDepthRatio: Float? = null
        private set

    private var prevHipY: Float? = null
    private var risingFrameCount = 0
    private var standStableFrameCount = 0
    private var peakHipYInDown: Float? = null

    /**
     * 餵入一幀「已通過品質過濾與 EMA 平滑」的關鍵點，回傳更新後的狀態。
     * [keyPointsByType] 必須包含左右髖與左右踝，缺少時本幀不更新狀態。
     */
    fun update(keyPointsByType: Map<KeyPointType, KeyPoint>): SquatState {
        val hipY = averageY(keyPointsByType, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP)
        if (hipY == null || normalizeScale <= 0f) return state

        // depthRatio 正值代表髖部低於站立基準（往下蹲），以髖-踝距離正規化避免受拍攝距離影響。
        val depthRatio = (hipY - standBaselineY) / normalizeScale

        when (state) {
            SquatState.STAND -> {
                if (depthRatio > Config.DOWN_ENTER_RATIO) {
                    state = SquatState.DOWN
                    risingFrameCount = 0
                    peakHipYInDown = hipY
                }
            }

            SquatState.DOWN -> {
                peakHipYInDown = maxOf(peakHipYInDown ?: hipY, hipY)
                val prev = prevHipY
                risingFrameCount = if (prev != null && hipY < prev) risingFrameCount + 1 else 0
                if (risingFrameCount >= Config.TURN_CONFIRM_FRAMES) {
                    val peakHipY = peakHipYInDown ?: hipY
                    lastBottomDepthRatio = (peakHipY - standBaselineY) / normalizeScale
                    state = SquatState.BOTTOM
                }
            }

            SquatState.BOTTOM -> {
                // BOTTOM 僅代表最低點判定瞬間，姿勢判定（M4）與深度回饋（M3）掛在此處觸發一次後立即進入 UP。
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
