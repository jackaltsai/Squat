package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType

/**
 * 五階段計次狀態機：STAND → DOWN → BOTTOM → UP → STAND（計次 +1）。
 *
 * 狀態機本身**不知道自己在數什麼動作**，只看 [RepSignal] 給的單一「動作進度」純量
 * （0 = 站姿靜止，正值 = 往動作方向前進）。深蹲往下、雙臂高舉往上，在這個抽象下
 * 都是「進度變大再變小」，因此同一份轉折邏輯可以共用。
 *
 * 階段名稱沿用論文 3.4 的五階段命名（也是研究模式 CSV 既有資料的欄位值），
 * 對非深蹲動作請讀成：**DOWN = 進入動作，BOTTOM = 動作極點，UP = 返回中**。
 * CSV 已有 `exerciseType` 欄位可據以判讀（2026-10-01 補上）。
 *
 * 最低點（BOTTOM）依據「進度由增轉減的轉折點 + 連續幀確認」，而非單一高度閾值
 * （見 CLAUDE.md 第 7 節）。判據是「已從本次峰值回退多少」而不是「比上一幀小」：
 * 逐幀比較在慢速動作時會被雜訊打斷而不斷歸零（長輩從椅子慢慢站起來整下不計次），
 * 在動作極點停頓時又會被雜訊湊出假的連續回退（坐在椅面上就誤觸發）。
 *
 * [RepSignal] 中的站立基準與比例尺來自站姿校正，此後整場訓練固定不變，
 * 確保達成率 p 的計算基準前後一致。
 */
class SquatStateMachine(private val signal: RepSignal) {
    var state: SquatState = SquatState.STAND
        private set
    var repCount: Int = 0
        private set

    /**
     * 最近一次 BOTTOM 的峰值進度，即達成率 p 的分子。
     * 深蹲家族的分母是校正取得的 `Duser`，其餘動作的分母是 [RepSignal.target]。
     */
    var lastPeakProgress: Float? = null
        private set

    private var retreatFrameCount = 0
    private var standStableFrameCount = 0
    private var peakProgressInRep: Float? = null

    /** 餵入一幀「已通過品質過濾與 EMA 平滑」的關鍵點，回傳更新後的狀態。 */
    fun update(keyPointsByType: Map<KeyPointType, KeyPoint>): SquatState {
        val progress = signal.progress(keyPointsByType) ?: return state

        when (state) {
            SquatState.STAND -> {
                if (progress > signal.enterThreshold) {
                    state = SquatState.DOWN
                    retreatFrameCount = 0
                    peakProgressInRep = progress
                }
            }

            SquatState.DOWN -> {
                val peak = maxOf(peakProgressInRep ?: progress, progress)
                peakProgressInRep = peak
                val retreatedFromPeak = peak - progress
                retreatFrameCount = if (retreatedFromPeak >= signal.turnConfirmRise) {
                    retreatFrameCount + 1
                } else {
                    0
                }
                if (retreatFrameCount >= Config.TURN_CONFIRM_FRAMES) {
                    // 取本次動作實際到達的峰值，與轉折何時被確認無關 ——
                    // 所以改用回退量判定不會改變 p 的語意，既有紀錄仍可比較。
                    lastPeakProgress = peak
                    state = SquatState.BOTTOM
                }
            }

            SquatState.BOTTOM -> {
                // BOTTOM 僅代表極點判定的那一瞬間，姿勢判定（M4）與深度回饋（M3）
                // 掛在此處各觸發一次後立即進入 UP，避免提示頻繁閃爍。
                state = SquatState.UP
                standStableFrameCount = 0
            }

            SquatState.UP -> {
                standStableFrameCount = if (progress < signal.returnThreshold) {
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
}
