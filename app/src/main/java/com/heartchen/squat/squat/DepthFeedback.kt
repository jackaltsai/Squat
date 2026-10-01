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
}

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
    }
}

/** p = 峰值進度 / 分母（深蹲家族為校正所得 `Duser`，其餘動作為 `RepSignal.target`）。 */
fun evaluateDepthFeedback(p: Float, mode: TrainingMode): DepthFeedback = when {
    p >= mode.greenThreshold -> DepthFeedback.GREEN
    p >= mode.yellowThreshold -> DepthFeedback.YELLOW
    else -> DepthFeedback.RED
}
