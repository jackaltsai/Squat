package com.heartchen.squat.squat

/**
 * 每次 BOTTOM 觸發時，依達成率 p 與訓練模式判定的三段式回饋（論文表 1）。
 *
 * ⚠️ **這個 enum 只表示等級，不帶文案。** 原本每個值自帶一句話（「蹲太淺了」等），
 * 那在只有深蹲時沒問題，但雙臂高舉舉不夠高時會顯示並用 TTS 念出「蹲太淺了」。
 * 文案改由 [ExerciseType.feedback] 提供。
 *
 * 名稱會以字串寫進 Room（`SquatRepRecord.feedbackColor`），**不可更動**。
 */
enum class DepthFeedback {
    GREEN,
    YELLOW,
    RED,

    /**
     * **這一下量到的幅度超出生理可能範圍，所以沒有評級。**
     *
     * 不是第四個等級 —— 論文表 1 仍然是三段。這個值表達的是
     * 「量測不可信，我們不知道使用者做了什麼」，發生在評級**之前**。
     *
     * 2026-10-12 實機（30 下）第 21、22 下記到 `dNow` 0.3763 / 0.2231，
     * 換算約 32cm / 19cm，而完整提踵只有 8~12cm。同樣的異常出現過三次
     * （10-05 第 4 下 0.1636、10-12 12:04 第 1 下 0.3327），
     * **每一次都被存成 GREEN** —— 系統對著一個它根本沒量準的動作說「做得好」，
     * 而那些紀錄會直接污染 M5 的驗證集。
     *
     * 最可能的成因是使用者在倒數取基準之後移動了（踮腳尖為了保住振幅必須用
     * 固定分母，對此天生敏感），其次是「屈膝再站直」——
     * 那是已記錄的限制：訊號量的是髖相對踩地腳尖的上升，兩者分不開。
     *
     * ⚠️ 寫 GREEN 是「你做得很好」、寫 RED 是「你做得不夠」，**兩個都沒有根據**。
     * 三段式 enum 表達不出「不知道」，所以這個值是必要的。
     */
    SUSPECT,
}

/**
 * [DepthFeedback.SUSPECT] 的文案。
 *
 * 不放進 [FeedbackMessages]：那裡的用語因動作而異（深蹲講「深」、舉手講「高」），
 * 而這一句講的是**量測**而不是動作，六個動作完全相同。
 *
 * 用字與 `FramingIssue.STUCK` 刻意一致 —— 最可能的成因就是使用者離開了
 * 取基準時的位置，而使用者已經從那裡學過這句話。
 */
const val SUSPECT_MESSAGE = "請站回原來位置"

/**
 * 三段式回饋的文案。用語因動作而異 —— 深蹲講「深」、舉手講「高」，
 * 而這些字同時會被 TTS 念出來給兩公尺外的使用者聽，講錯會直接誤導動作。
 */
data class FeedbackMessages(
    val green: String,
    val yellow: String,
    val red: String,
) {
    fun of(feedback: DepthFeedback): String = when (feedback) {
        DepthFeedback.GREEN -> green
        DepthFeedback.YELLOW -> yellow
        DepthFeedback.RED -> red
        // 量測不可信時講的是量測，不是動作 —— 所以用共用文案，不是這三句之一。
        DepthFeedback.SUSPECT -> SUSPECT_MESSAGE
    }
}

/**
 * p = 峰值進度 / 分母（深蹲家族為校正所得 `Duser`，其餘動作為 `RepSignal.target`）。
 *
 * **這是論文表 1 本身，刻意保持純粹的三段。** 生理合理性是另一回事，
 * 在 [gradeRep] 裡、在呼叫這裡**之前**處理。
 */
fun evaluateDepthFeedback(p: Float, mode: TrainingMode): DepthFeedback = when {
    p >= mode.greenThreshold -> DepthFeedback.GREEN
    p >= mode.yellowThreshold -> DepthFeedback.YELLOW
    else -> DepthFeedback.RED
}

/**
 * 一下完成時的評級：先問「這個量測可不可信」，再問「做得夠不夠」。
 *
 * [peakProgress] 是這一下的峰值進度（即 `dNow`，以動作自己的尺度正規化），
 * [plausibleMax] 是該動作宣告的生理上限（[RepSignal.plausibleMax]，null = 未知）。
 *
 * ⚠️ 用 [peakProgress] 而不是 p 來判斷。p 的分母對深蹲家族是校正所得的 `Duser`，
 * 校正做得淺時 p 會憑空變大 —— 拿 p 判斷會把「校正太淺」誤判成「量測不可信」。
 * 生理上限講的是**身體能動多少**，那要對著原始幅度問。
 */
fun gradeRep(
    peakProgress: Float,
    p: Float,
    mode: TrainingMode,
    plausibleMax: Float?
): DepthFeedback =
    if (plausibleMax != null && peakProgress > plausibleMax) {
        DepthFeedback.SUSPECT
    } else {
        evaluateDepthFeedback(p, mode)
    }
