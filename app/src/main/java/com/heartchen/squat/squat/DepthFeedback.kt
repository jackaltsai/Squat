package com.heartchen.squat.squat

/** 每次 BOTTOM 觸發時，依深度達成率 p 與所選訓練模式判定的三段式回饋。 */
enum class DepthFeedback(val message: String) {
    GREEN("深度達標！"),
    YELLOW("再蹲深一點"),
    RED("蹲太淺了")
}

/** p = Dnow / Duser，Dnow 為本次 BOTTOM 深度、Duser 為校正取得的個人化基準深度。 */
fun evaluateDepthFeedback(p: Float, mode: TrainingMode): DepthFeedback = when {
    p >= mode.greenThreshold -> DepthFeedback.GREEN
    p >= mode.yellowThreshold -> DepthFeedback.YELLOW
    else -> DepthFeedback.RED
}
