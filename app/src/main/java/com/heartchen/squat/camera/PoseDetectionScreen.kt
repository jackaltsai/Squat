package com.heartchen.squat.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.EmaSmoother
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import com.heartchen.squat.pose.PoseAnalyzer
import com.heartchen.squat.pose.PoseConfidenceList
import com.heartchen.squat.pose.PoseFrame
import com.heartchen.squat.pose.PoseOverlay
import com.heartchen.squat.pose.passesQualityCheck
import com.heartchen.squat.squat.SquatState
import com.heartchen.squat.squat.SquatStateMachine
import java.util.concurrent.Executors

private const val TAG = "PoseDetectionScreen"

/**
 * M1+M2 Demo 畫面：CameraX 即時預覽 + ML Kit 骨架疊圖 + 品質過濾/EMA 平滑 + 五階段狀態機自動計次。
 *
 * 固定使用後鏡頭：前鏡頭在「手機立於前方、全身入鏡深蹲」的情境下角度不好抓，不提供切換。
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PoseDetectionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var poseFrame by remember { mutableStateOf<PoseFrame?>(null) }
    var previewViewSize by remember { mutableStateOf(IntSize.Zero) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var squatState by remember { mutableStateOf(SquatState.STAND) }
    var repCount by remember { mutableIntStateOf(0) }

    val emaSmoother = remember { EmaSmoother(Config.EMA_ALPHA) }
    val stateMachine = remember { SquatStateMachine() }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    DisposableEffect(previewView) {
        val pv = previewView
        if (pv == null) {
            return@DisposableEffect onDispose {}
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val analyzer = PoseAnalyzer(isFrontCamera = false) { frame ->
            poseFrame = frame
            if (frame != null && frame.passesQualityCheck()) {
                val rawHipY = averageHipY(frame)
                val smoothedByType = emaSmoother.smooth(frame.keyPoints).associateBy { it.type }
                val emaHipY = averageHipY(smoothedByType[KeyPointType.LEFT_HIP], smoothedByType[KeyPointType.RIGHT_HIP])
                squatState = stateMachine.update(smoothedByType)
                repCount = stateMachine.repCount
                Log.d(
                    TAG,
                    "hipY(raw)=$rawHipY hipY(ema)=$emaHipY state=$squatState count=$repCount"
                )
            }
        }

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pv.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            poseFrame = null
            cameraProviderFuture.get().unbindAll()
            analyzer.close()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    previewView = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewViewSize = it }
        )

        poseFrame?.let { frame ->
            PoseOverlay(
                poseFrame = frame,
                viewSize = previewViewSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        val currentFrame = poseFrame
        Text(
            text = if (currentFrame == null) {
                "偵測不到關鍵點"
            } else {
                "已偵測 ${currentFrame.keyPoints.size}/6 個關鍵點"
            },
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "${squatState.name}　次數 $repCount",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        PoseConfidenceList(
            poseFrame = currentFrame,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

private fun averageHipY(frame: PoseFrame): Float? {
    val byType = frame.keyPoints.associateBy { it.type }
    return averageHipY(byType[KeyPointType.LEFT_HIP], byType[KeyPointType.RIGHT_HIP])
}

private fun averageHipY(leftHip: KeyPoint?, rightHip: KeyPoint?): Float? {
    if (leftHip == null || rightHip == null) return null
    return (leftHip.y + rightHip.y) / 2f
}
