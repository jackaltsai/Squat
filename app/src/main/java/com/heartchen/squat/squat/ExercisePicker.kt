package com.heartchen.squat.squat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SCRIM = Color(0xFF0B1B2B)
private val CARD = Color(0xFF1B2A38)
private val ACCENT = Color(0xFF1FC8A9)
private val MUTED = Color(0xFF8FA6B6)
private val DISABLED = Color(0xFF4A5C6B)

/**
 * 起始畫面：動作選擇格線（每列三格）。
 *
 * 尚未實作偵測的動作仍然顯示，但整格變灰並標示「準備中」——
 * 直接隱藏會讓使用者不知道還有哪些動作；讓它可點進去卻毫無反應，
 * 則會讓人以為是相機壞了。標灰是唯一誠實的做法。
 */
@Composable
fun ExercisePicker(
    onSelect: (ExerciseType) -> Unit,
    onShowStats: () -> Unit,
    onExportAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "選擇訓練動作",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            ExerciseType.GRID_ORDER.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { type ->
                        ExerciseCard(
                            type = type,
                            onClick = { onSelect(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "訓練統計",
                color = Color.White,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color(0xFF00695C), RoundedCornerShape(12.dp))
                    .clickable { onShowStats() }
                    .padding(vertical = 12.dp)
            )
            Text(
                text = "匯出全部歷史紀錄",
                color = MUTED,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExportAll() }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    type: ExerciseType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = type.detectionImplemented
    val tint = if (enabled) ACCENT else DISABLED
    val labelColor = if (enabled) Color.White else DISABLED

    Column(
        modifier = modifier
            .aspectRatio(0.82f)
            .background(CARD, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExerciseIcon(
            type = type,
            tint = tint,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            text = type.label,
            color = labelColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (!enabled) {
            Text(
                text = "準備中",
                color = DISABLED,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
