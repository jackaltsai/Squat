package com.heartchen.squat.pose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import android.graphics.Paint
import kotlin.math.min

/** 信心值低於此門檻時，關鍵點以紅色標示（僅供 M1 肉眼判斷穩定度使用，非狀態機門檻）。 */
private const val CONFIDENCE_WARNING_THRESHOLD = 0.6f

private val skeletonConnections = listOf(
    KeyPointType.LEFT_HIP to KeyPointType.RIGHT_HIP,
    KeyPointType.LEFT_HIP to KeyPointType.LEFT_KNEE,
    KeyPointType.LEFT_KNEE to KeyPointType.LEFT_ANKLE,
    KeyPointType.RIGHT_HIP to KeyPointType.RIGHT_KNEE,
    KeyPointType.RIGHT_KNEE to KeyPointType.RIGHT_ANKLE,
)

/**
 * 將 [poseFrame] 的髖/膝/踝關鍵點與骨架連線疊圖在 [viewSize] 大小的畫面上，
 * 並標示各點的信心值，供 M1 肉眼判斷抓取穩定度。
 */
@Composable
fun PoseOverlay(
    poseFrame: PoseFrame,
    viewSize: IntSize,
    modifier: Modifier = Modifier
) {
    if (viewSize.width == 0 || viewSize.height == 0) return

    val pointsByType = poseFrame.keyPoints.associateBy { it.type }
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }

    Canvas(modifier = modifier) {
        val scale = min(
            size.width / poseFrame.imageWidth.toFloat(),
            size.height / poseFrame.imageHeight.toFloat()
        )
        val offsetX = (size.width - poseFrame.imageWidth * scale) / 2f
        val offsetY = (size.height - poseFrame.imageHeight * scale) / 2f

        fun toScreen(point: KeyPoint): Offset {
            val mirroredX = if (poseFrame.isFrontCamera) poseFrame.imageWidth - point.x else point.x
            return Offset(offsetX + mirroredX * scale, offsetY + point.y * scale)
        }

        skeletonConnections.forEach { (fromType, toType) ->
            val from = pointsByType[fromType] ?: return@forEach
            val to = pointsByType[toType] ?: return@forEach
            drawLine(
                color = Color(0xFF00E5FF),
                start = toScreen(from),
                end = toScreen(to),
                strokeWidth = 6f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        poseFrame.keyPoints.forEach { point ->
            val screenPos = toScreen(point)
            val color = if (point.inFrameLikelihood >= CONFIDENCE_WARNING_THRESHOLD) {
                Color(0xFF00E676)
            } else {
                Color(0xFFFF1744)
            }
            drawCircle(color = color, radius = 12f, center = screenPos)
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = screenPos,
                style = Stroke(width = 2f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${point.type.label} ${"%.2f".format(point.inFrameLikelihood)}",
                screenPos.x + 16f,
                screenPos.y,
                textPaint
            )
        }
    }
}
