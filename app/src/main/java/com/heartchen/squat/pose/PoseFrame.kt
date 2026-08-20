package com.heartchen.squat.pose

/** 信心值低於此門檻時視為不穩定（僅供 M1 肉眼判斷抓取穩定度使用，非狀態機門檻）。 */
const val CONFIDENCE_WARNING_THRESHOLD = 0.6f

/** 單一關鍵點的偵測結果，座標為「已依裝置方向校正後」的影像座標系。 */
data class KeyPoint(
    val type: KeyPointType,
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float
)

enum class KeyPointType(val label: String) {
    LEFT_HIP("L_HIP"),
    RIGHT_HIP("R_HIP"),
    LEFT_KNEE("L_KNEE"),
    RIGHT_KNEE("R_KNEE"),
    LEFT_ANKLE("L_ANKLE"),
    RIGHT_ANKLE("R_ANKLE"),
}

/**
 * 一幀的姿態偵測結果。
 * [imageWidth] / [imageHeight] 為已依旋轉校正後的「直立」影像尺寸，
 * 對應 [keyPoints] 的座標系；疊圖時需以此換算到畫面座標。
 */
data class PoseFrame(
    val keyPoints: List<KeyPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val isFrontCamera: Boolean
)
