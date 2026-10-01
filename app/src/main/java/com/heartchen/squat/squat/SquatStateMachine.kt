package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType

/**
 * 計次狀態機：STAND → DOWN → BOTTOM → UP → STAND（計次 +1）。
 *
 * 深蹲與坐站練習共用這個狀態機 —— 兩者的軌跡都是「髖部下降再上升」，
 * 差別只在深度被椅面限制，而深度本來就是用使用者自己的 Duser 正規化的。
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
                val peakHipY = maxOf(peakHipYInDown ?: hipY, hipY)
                peakHipYInDown = peakHipY
                // 判據是「已從本次最低點回升超過一定距離」，而不是「比上一幀高」。
                // 逐幀比較在慢速起身時會被雜訊打斷而不斷歸零，在坐著停頓時又會被雜訊湊出
                // 假的連續上升；改用回升量則與速度無關，停頓時回升量為 0 也不會誤觸發。
                val risenFromPeak = (peakHipY - hipY) / normalizeScale
                risingFrameCount = if (risenFromPeak >= Config.TURN_CONFIRM_RISE_RATIO) {
                    risingFrameCount + 1
                } else {
                    0
                }
                if (risingFrameCount >= Config.TURN_CONFIRM_FRAMES) {
                    // 深度仍取本次下降過程的最低點，與轉折何時被確認無關，
                    // 所以這個改動不會改變 lastBottomDepthRatio 的語意。
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
