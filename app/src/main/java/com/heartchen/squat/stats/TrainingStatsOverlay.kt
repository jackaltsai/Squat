package com.heartchen.squat.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

private val CARD = Color(0xFF1B2A38)
private val ACCENT = Color(0xFF1FC8A9)
private val MUTED = Color(0xFF8FA6B6)

/** 圖表顯示的天數。14 天剛好涵蓋兩個完整週期，又不會讓每根柱子細到看不出來。 */
const val STATS_CHART_DAYS = 14

/**
 * 訓練統計：今日／本週／本月的次數、最近 14 天長條圖、每日明細。
 *
 * 資料全部來自裝置本機的 Room 資料庫，不需要網路。
 */
@Composable
fun TrainingStatsOverlay(
    summary: TrainingSummary?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // 完全不透明：這是一個全螢幕頁面，不是疊在相機上的提示。
            // 留 3% 透明度並不會產生層次感，只會讓後面的「選擇訓練模式」、除錯模式開關
            // 與框位警告透出來變成雜訊。
            .background(Color(0xFF0B1B2B))
            // 擋掉點擊，避免點統計頁時誤觸到後面的相機 UI
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("訓練統計", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "關閉",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (summary == null) {
                Text("讀取中…", color = MUTED, fontSize = 16.sp)
                return@Column
            }

            if (summary.totalReps == 0) {
                Text(
                    text = "還沒有任何訓練紀錄。\n完成一組深蹲後，這裡就會開始累積。",
                    color = MUTED,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("今日", summary.todayReps, Modifier.weight(1f), highlight = true)
                SummaryCard("本週", summary.weekReps, Modifier.weight(1f))
                SummaryCard("本月", summary.monthReps, Modifier.weight(1f))
            }

            Text(
                text = "累計 ${summary.totalReps} 下　最近 $STATS_CHART_DAYS 天有 ${summary.activeDays} 天有訓練",
                color = MUTED,
                fontSize = 14.sp
            )

            SectionTitle("最近 $STATS_CHART_DAYS 天")
            DailyBarChart(summary.days)

            SectionTitle("每日明細")
            val activeDays = summary.days.filter { it.reps > 0 }.reversed()
            if (activeDays.isEmpty()) {
                Text("最近 $STATS_CHART_DAYS 天沒有訓練紀錄", color = MUTED, fontSize = 14.sp)
            } else {
                activeDays.forEach { DayRow(it) }
            }

            Text(
                text = "統計資料僅儲存於本機，不會上傳。",
                color = MUTED,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SummaryCard(label: String, reps: Int, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .background(if (highlight) ACCENT.copy(alpha = 0.18f) else CARD, RoundedCornerShape(14.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = MUTED, fontSize = 14.sp)
        Text(
            text = reps.toString(),
            color = if (highlight) ACCENT else Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Text("下", color = MUTED, fontSize = 12.sp)
    }
}

/**
 * 每日次數長條圖。
 *
 * 柱高以「這段期間的單日最高次數」為滿格，而不是固定上限 —— 每個人的訓練量差距很大，
 * 固定上限會讓量少的人整排看起來都是貼地的短柱，看不出相對變化。
 */
@Composable
private fun DailyBarChart(days: List<DayBucket>) {
    val maxReps = days.maxOfOrNull { it.reps } ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = if (day.reps > 0) day.reps.toString() else "",
                    color = MUTED,
                    fontSize = 10.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 沒訓練的日子畫一條極細的底線，留一個看得見的空格才能區分
                    // 「連續練了三天」與「三天裡只練了一天」。
                    val fraction = if (maxReps > 0) day.reps.toFloat() / maxReps else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                            .background(
                                if (day.reps > 0) ACCENT else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(DateBuckets.weekdayLabel(day.startMillis), color = MUTED, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DayRow(day: DayBucket) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${DateBuckets.dayLabel(day.startMillis)}（${DateBuckets.weekdayLabel(day.startMillis)}）",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = "${day.reps} 下",
            color = ACCENT,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = "達標 ${day.greenRatio}%",
            color = MUTED,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
