package com.heartchen.squat.stats

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
import com.heartchen.squat.squat.ExerciseType

private val SCRIM = Color(0xFF0B1B2B)
private val MUTED = Color(0xFFB4C6D4)

// 卡片底色與動作選擇格線完全一致 —— 使用者看到的是同一組圖、同一種卡片，
// 只是上面多了一個數字。淺底的理由見 ExercisePicker：
// 動作圖是白人偶配柔和投影，本來就是為淺底畫的。
private val CARD = Color(0xFFFFFFFF)
private val LABEL = Color(0xFF16232E)

// 有做過 → 實色徽章；沒做過 → 淡色，但**仍然顯示 0**。
// 把沒做的動作隱藏或留白，使用者會不知道那格是「沒做」還是「壞了」。
private val BADGE_DONE = Color(0xFF00695C)
private val BADGE_NONE = Color(0xFFDCE4EA)
private val BADGE_DONE_TEXT = Color.White
private val BADGE_NONE_TEXT = Color(0xFF7A8A96)

/**
 * 訓練統計（M9 重新設計，長者取向）。
 *
 * **只有六張動作圖，每張圖裡顯示那個動作當天做了幾下。** 其他全部移除。
 *
 * M6 的版本是「今日／本週／本月三個大數字 + 最近 14 天長條圖 + 每日明細」。
 * 對長者而言那是三種不同的時間尺度加一張要解讀的圖表 ——
 * 而他真正想知道的只有一件事：**今天這個動作做了幾下**。
 *
 * 設計上刻意與動作選擇格線**用同一組圖、同一種卡片**：
 * 使用者不需要再學一套新的視覺語言，一眼就知道哪一格對應哪個動作。
 *
 * 日期可以往前翻（`‹` / `›`），因為「每日」若只能看今天就答不出「昨天做了多少」。
 * 翻到今天就不能再往後 —— 未來的日期沒有意義，按鈕會變淡且不可按。
 */
@Composable
fun TrainingStatsOverlay(
    counts: DailyExerciseCounts?,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM.copy(alpha = 0.97f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DayHeader(
                counts = counts,
                isToday = isToday,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay
            )

            if (counts == null) {
                Text("讀取中…", color = MUTED, fontSize = 20.sp)
            } else {
                // 格線順序與動作選擇畫面相同，位置才會對得上。
                ExerciseType.GRID_ORDER.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { type ->
                            ExerciseCountCard(
                                type = type,
                                reps = counts.repsOf(type),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }

                Text(
                    text = if (counts.totalReps > 0) {
                        "這天總共 ${counts.totalReps} 下"
                    } else {
                        "這天還沒有訓練紀錄"
                    },
                    color = MUTED,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            Text(
                text = "關閉",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(Color(0xFF37474F), RoundedCornerShape(14.dp))
                    .clickable { onClose() }
                    .padding(vertical = 18.dp)
            )
        }
    }
}

@Composable
private fun DayHeader(
    counts: DailyExerciseCounts?,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayArrow(text = "‹", enabled = true, onClick = onPreviousDay)
        Text(
            text = dayHeading(counts, isToday),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        // 今天之後沒有紀錄可看，按鈕變淡且不可按 —— 比按了沒反應誠實。
        DayArrow(text = "›", enabled = !isToday, onClick = onNextDay)
    }
}

/** 箭頭的點擊範圍刻意開大（長者的手指與視力都需要），字級也比一般按鈕大。 */
@Composable
private fun DayArrow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (enabled) Color.White else Color(0xFF3C4F60),
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(
                if (enabled) Color(0xFF1B3346) else Color(0xFF13222F),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

private fun dayHeading(counts: DailyExerciseCounts?, isToday: Boolean): String {
    if (counts == null) return "訓練紀錄"
    val date = DateBuckets.dayLabel(counts.dayStartMillis)
    val weekday = DateBuckets.weekdayLabel(counts.dayStartMillis)
    return if (isToday) "今天 $date（$weekday）" else "$date（$weekday）"
}

/**
 * 一張動作卡：圖片 + **疊在圖片裡**的次數徽章 + 動作名稱。
 *
 * 徽章放在圖片下緣中央（人偶的腳的位置），而不是正中央 ——
 * 正中央會蓋掉動作本身最好認的部分（軀幹與手臂的姿勢）。
 */
@Composable
private fun ExerciseCountCard(
    type: ExerciseType,
    reps: Int,
    modifier: Modifier = Modifier
) {
    val done = reps > 0
    Column(
        modifier = modifier
            .background(CARD, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(type.iconRes),
                contentDescription = type.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        if (done) BADGE_DONE else BADGE_NONE,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = reps.toString(),
                    color = if (done) BADGE_DONE_TEXT else BADGE_NONE_TEXT,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "下",
                    color = if (done) BADGE_DONE_TEXT else BADGE_NONE_TEXT,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
                )
            }
        }
        Text(
            text = type.label,
            color = LABEL,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
