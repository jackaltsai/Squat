package com.heartchen.squat.pose

/** 單一關鍵點的偵測結果，座標為「已依裝置方向校正後」的影像座標系。 */
data class KeyPoint(
    val type: KeyPointType,
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float
)

/**
 * 追蹤的關鍵點。
 *
 * ⚠️ 新增項目時務必記得：品質檢查**不是**檢查這個 enum 的全部項目，而是檢查
 * 「當前動作宣告需要的那些點」（見 [PoseFrame.passesQualityCheck]）。
 * 舊版是拿 `KeyPointType.entries.size` 當門檻，一旦擴充就會要求全部關鍵點
 * 都偵測到且信心值達標 —— 手腕、手肘在運動中很容易掉點，結果會是幾乎每一幀
 * 都被丟棄、App 表面上完全沒反應。
 */
enum class KeyPointType(val label: String) {
    LEFT_SHOULDER("L_SHLD"),
    RIGHT_SHOULDER("R_SHLD"),
    LEFT_ELBOW("L_ELBW"),
    RIGHT_ELBOW("R_ELBW"),
    LEFT_WRIST("L_WRST"),
    RIGHT_WRIST("R_WRST"),
    LEFT_HIP("L_HIP"),
    RIGHT_HIP("R_HIP"),
    LEFT_KNEE("L_KNEE"),
    RIGHT_KNEE("R_KNEE"),
    LEFT_ANKLE("L_ANKLE"),
    RIGHT_ANKLE("R_ANKLE"),
    ;

    companion object {
        /** 深蹲家族（深蹲、坐站、高抬腿、踮腳尖）需要的下肢六點。 */
        val LOWER_BODY: Set<KeyPointType> = setOf(
            LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
        )

        /** 手臂動作（高舉、側平舉、擴胸推掌）需要的上肢六點。 */
        val UPPER_BODY: Set<KeyPointType> = setOf(
            LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST
        )

        /** 軀幹側彎等需要肩髖相對位置的動作。 */
        val TORSO: Set<KeyPointType> = setOf(
            LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP
        )
    }
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
