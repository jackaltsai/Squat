package com.heartchen.squat.squat

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SCRIM = Color(0xFF0B1B2B)
private val MUTED = Color(0xFFB4C6D4)

/**
 * 卡片底色刻意是淺色的。
 *
 * 動作圖是白色人偶配柔和投影，本來就是為淺底畫的（App 圖示也是白底）。
 * 放在深色卡片上時，投影會變成一塊灰污漬、JPEG 邊緣雜訊會變成暗色鑲邊，
 * 要一路修圖才壓得下來；順著素材用淺底則什麼都不必做。
 */
private val CARD_ENABLED = Color(0xFFFFFFFF)
private val CARD_DISABLED = Color(0xFFCFD8DF)
private val LABEL_ENABLED = Color(0xFF16232E)
private val LABEL_DISABLED = Color(0xFF6B7A86)

/**
 * 起始畫面：動作選擇格線。
 *
 * **主要使用者是長者，所有尺寸都往大的調：**
 * - 每列兩格而非三格。六個動作排成 2×3，卡片寬度比三欄時大 5 成，圖示與文字跟著放大。
 * - 卡片高度**不寫死**（不用 aspectRatio），由內容撐開。長輩常把系統字級調大，
 *   若固定高度又限制行數，放大字級只會讓文字被截斷，等於沒有放大。
 * - 文字一律 `sp`，跟著系統字級一起放大。
 *
 * 尚未實作偵測的動作仍然顯示，但整格變淡並標示「準備中」——
 * 直接隱藏會讓使用者不知道還有哪些動作；讓它可點進去卻毫無反應，
 * 則會讓人以為是相機壞了。標示狀態是唯一誠實的做法。
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
            .background(SCRIM.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "選擇訓練動作",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            ExerciseType.GRID_ORDER.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { type ->
                        ExerciseCard(
                            type = type,
                            onClick = { onSelect(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 動作數為奇數時補一個等寬空格，最後一張才不會被撐成整列寬
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }

            Text(
                text = "訓練統計",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Color(0xFF00695C), RoundedCornerShape(14.dp))
                    .clickable { onShowStats() }
                    .padding(vertical = 18.dp)
            )
            Text(
                text = "匯出全部歷史紀錄",
                color = MUTED,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExportAll() }
                    .padding(vertical = 14.dp)
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

    Column(
        modifier = modifier
            .background(if (enabled) CARD_ENABLED else CARD_DISABLED, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(type.iconRes),
            contentDescription = type.label,
            contentScale = ContentScale.Fit,
            // 停用態用降透明度而非套灰色濾鏡：人偶的立體感全靠明暗層次，
            // 一律染成同一個灰會把它壓成剪影，反而看不出是什麼動作。
            alpha = if (enabled) 1f else 0.4f,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
        )
        Text(
            text = type.label,
            color = if (enabled) LABEL_ENABLED else LABEL_DISABLED,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (!enabled) {
            Text(
                text = "準備中",
                color = LABEL_DISABLED,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
