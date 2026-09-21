package com.heartchen.squat.pose

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

/**
 * M1：只負責把 CameraX 的每一幀丟給 ML Kit PoseDetector，
 * 擷取髖/膝/踝六個關鍵點後回呼給上層。品質過濾與 EMA 平滑留到 M2 處理。
 */
class PoseAnalyzer(
    private val isFrontCamera: Boolean,
    private val onResult: (PoseFrame?) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    // ML Kit 本來就會計算全部 33 個 landmark，這裡挑幾個只影響下游處理量，
    // 不影響推論成本，所以上半身一併取出供手臂類動作使用。
    private val landmarksOfInterest = listOf(
        PoseLandmark.LEFT_SHOULDER to KeyPointType.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER to KeyPointType.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW to KeyPointType.LEFT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to KeyPointType.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST to KeyPointType.LEFT_WRIST,
        PoseLandmark.RIGHT_WRIST to KeyPointType.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP to KeyPointType.LEFT_HIP,
        PoseLandmark.RIGHT_HIP to KeyPointType.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE to KeyPointType.LEFT_KNEE,
        PoseLandmark.RIGHT_KNEE to KeyPointType.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE to KeyPointType.LEFT_ANKLE,
        PoseLandmark.RIGHT_ANKLE to KeyPointType.RIGHT_ANKLE,
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { pose -> onResult(toPoseFrame(pose, imageProxy, rotationDegrees)) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun toPoseFrame(pose: Pose, imageProxy: ImageProxy, rotationDegrees: Int): PoseFrame? {
        val keyPoints = landmarksOfInterest.mapNotNull { (landmarkType, type) ->
            pose.getPoseLandmark(landmarkType)?.let { landmark ->
                KeyPoint(
                    type = type,
                    x = landmark.position.x,
                    y = landmark.position.y,
                    inFrameLikelihood = landmark.inFrameLikelihood
                )
            }
        }
        if (keyPoints.isEmpty()) return null

        // rotationDegrees 90/270 代表感光元件方向與直立顯示相差 90 度，
        // ML Kit 回傳的座標已對齊旋轉後的直立影像，因此寬高需對應互換。
        val isSideways = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isSideways) imageProxy.height else imageProxy.width
        val imageHeight = if (isSideways) imageProxy.width else imageProxy.height

        return PoseFrame(
            keyPoints = keyPoints,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            isFrontCamera = isFrontCamera
        )
    }

    fun close() {
        detector.close()
    }
}
