package com.heartchen.squat.pose

import android.graphics.PointF

/**
 * 對關鍵點座標做指數移動平均（EMA）平滑。每個 [KeyPointType] 各自維護獨立的平滑狀態，
 * 需在同一次相機串流的生命週期內重複使用同一個實例，才能累積平滑效果。
 */
class EmaSmoother(private val alpha: Float) {
    private val smoothed = mutableMapOf<KeyPointType, PointF>()

    fun smooth(keyPoints: List<KeyPoint>): List<KeyPoint> = keyPoints.map { point ->
        val prev = smoothed[point.type]
        val newX: Float
        val newY: Float
        if (prev == null) {
            newX = point.x
            newY = point.y
        } else {
            newX = alpha * point.x + (1 - alpha) * prev.x
            newY = alpha * point.y + (1 - alpha) * prev.y
        }
        smoothed[point.type] = PointF(newX, newY)
        point.copy(x = newX, y = newY)
    }
}
