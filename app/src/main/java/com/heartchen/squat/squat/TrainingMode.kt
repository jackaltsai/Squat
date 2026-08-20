package com.heartchen.squat.squat

/**
 * 訓練模式，對應論文表 1 的三段式深度達成率門檻：
 * p = Dnow / Duser 大於等於 greenThreshold 為綠色，介於 yellowThreshold 與 greenThreshold 之間為黃色，
 * 低於 yellowThreshold 為紅色。
 */
enum class TrainingMode(val label: String, val greenThreshold: Float, val yellowThreshold: Float) {
    BEGINNER("入門 L1", 0.90f, 0.80f),
    NORMAL("一般 L2", 1.00f, 0.85f),
    ADVANCED("進階 L3", 1.10f, 0.95f),
}
