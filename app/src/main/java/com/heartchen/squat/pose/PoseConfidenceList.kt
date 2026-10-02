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

/** 下肢六點的固定顯示順序（左右成對，方便肉眼比對左右是否一致）。 */
private val lowerBodyOrder = listOf(
    KeyPointType.LEFT_HIP,
    KeyPointType.RIGHT_HIP,
    KeyPointType.LEFT_KNEE,
    KeyPointType.RIGHT_KNEE,
    KeyPointType.LEFT_ANKLE,
    KeyPointType.RIGHT_ANKLE,
    KeyPointType.LEFT_HEEL,
    KeyPointType.RIGHT_HEEL,
    KeyPointType.LEFT_TOE,
    KeyPointType.RIGHT_TOE,
)

/** 上肢六點的固定顯示順序。 */
private val upperBodyOrder = listOf(
    KeyPointType.LEFT_SHOULDER,
    KeyPointType.RIGHT_SHOULDER,
    KeyPointType.LEFT_ELBOW,
    KeyPointType.RIGHT_ELBOW,
    KeyPointType.LEFT_WRIST,
    KeyPointType.RIGHT_WRIST,
)

/**
 * 列出**當前動作實際需要的**關鍵點信心值，供肉眼判斷抓取穩定度（字級加大方便實機閱讀）。
 *
 * [required] 來自 `ExerciseType.requiredPoints`。原本寫死下肢六點 ——
 * 這在只有深蹲時沒問題，但手臂動作除錯時會看著一整排「--」，
 * 完全看不到真正在用的肩/肘/腕信心值，等於研究模式對上肢動作失效。
 */
@Composable
fun PoseConfidenceList(
    poseFrame: PoseFrame?,
    required: Set<KeyPointType>,
    modifier: Modifier = Modifier
) {
    // 依固定順序顯示，而不是用 Set 的迭代順序 —— 每幀順序跳動的清單沒辦法用眼睛讀。
    // 先按固定順序排，再把順序表沒收錄到的（例如之後新增的關鍵點）接在後面 ——
    // 純粹 filter 的話，漏收錄的點會被靜默吞掉，除錯時看不到自己要的數字。
    val ordered = lowerBodyOrder + upperBodyOrder
    val displayOrder = ordered.filter { it in required } + required.filterNot { it in ordered }
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
