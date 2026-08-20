package com.heartchen.squat.pose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartchen.squat.config.Config
import java.util.Locale

private val displayOrder = listOf(
    KeyPointType.LEFT_HIP,
    KeyPointType.RIGHT_HIP,
    KeyPointType.LEFT_KNEE,
    KeyPointType.RIGHT_KNEE,
    KeyPointType.LEFT_ANKLE,
    KeyPointType.RIGHT_ANKLE,
)

/** 集中列出六個關鍵點的信心值，供 M1 肉眼判斷抓取穩定度用（字級加大方便實機閱讀）。 */
@Composable
fun PoseConfidenceList(poseFrame: PoseFrame?, modifier: Modifier = Modifier) {
    val pointsByType = poseFrame?.keyPoints?.associateBy { it.type } ?: emptyMap()

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        displayOrder.forEach { type ->
            val confidence = pointsByType[type]?.inFrameLikelihood
            val color = when {
                confidence == null -> Color.Gray
                confidence >= Config.CONFIDENCE_THRESHOLD -> Color(0xFF00E676)
                else -> Color(0xFFFF1744)
            }
            Text(
                text = "${type.label}  ${confidence?.let { String.format(Locale.US, "%.2f", it) } ?: "--"}",
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
