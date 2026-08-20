package com.heartchen.squat.pose

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
