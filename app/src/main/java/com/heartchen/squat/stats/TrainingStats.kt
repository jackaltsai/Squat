package com.heartchen.squat.stats

import com.heartchen.squat.data.SquatRepRecord
import com.heartchen.squat.squat.DepthFeedback
import java.util.Calendar

/**
 * 訓練統計的分組邏輯。
 *
 * 全部用 [Calendar] 而非 java.time：minSdk 是 24，java.time 要 API 26，
 * 在不開 core library desugaring 的前提下 Calendar 是唯一能用的選擇。
 *
 * 這裡是純函式，不碰資料庫也不碰 UI，方便之後補單元測試。
 */
object DateBuckets {

    /**
     * 一週從星期一算起。
     *
     * zh-TW locale 的 Calendar 預設 firstDayOfWeek 是星期日，跟一般人講「這週」的
     * 理解（週一到週日）不一致，所以明確指定，不吃 locale 預設值。
     */
    private fun calendar(millis: Long): Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = millis
    }

    private fun Calendar.atMidnight(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun startOfDay(millis: Long): Long = calendar(millis).atMidnight().timeInMillis

    fun startOfWeek(millis: Long): Long = calendar(millis).atMidnight().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }.timeInMillis

    fun startOfMonth(millis: Long): Long = calendar(millis).atMidnight().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /** 用 Calendar 加天數而不是加 86400000 毫秒，才能正確處理日光節約時間造成的 23/25 小時日。 */
    fun addDays(millis: Long, days: Int): Long =
        calendar(millis).apply { add(Calendar.DAY_OF_MONTH, days) }.timeInMillis

    fun dayLabel(millis: Long): String = calendar(millis).let {
        "%d/%d".format(it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    fun weekdayLabel(millis: Long): String = calendar(millis).let {
        when (it.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "一"
            Calendar.TUESDAY -> "二"
            Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"
            Calendar.FRIDAY -> "五"
            Calendar.SATURDAY -> "六"
            else -> "日"
        }
    }
}

/** 一天的彙總。[reps] 為 0 代表當天沒訓練，圖表仍要畫出這一格才看得出中斷。 */
data class DayBucket(
    val startMillis: Long,
    val reps: Int,
    val greenReps: Int,
    val valgusReps: Int
) {
    val greenRatio: Int get() = if (reps > 0) greenReps * 100 / reps else 0
}

data class TrainingSummary(
    val todayReps: Int,
    val weekReps: Int,
    val monthReps: Int,
    val totalReps: Int,
    val days: List<DayBucket>
) {
    val bestDayReps: Int get() = days.maxOfOrNull { it.reps } ?: 0
    val activeDays: Int get() = days.count { it.reps > 0 }
}

/**
 * 把紀錄分成最近 [dayCount] 天的每日桶，沒訓練的日子也會產生一個 reps = 0 的桶
 * （少了空白格，圖表會把「連續三天」和「三天裡只練一天」畫成一樣）。
 */
fun buildDailyBuckets(
    records: List<SquatRepRecord>,
    dayCount: Int,
    now: Long = System.currentTimeMillis()
): List<DayBucket> {
    val today = DateBuckets.startOfDay(now)
    val starts = (dayCount - 1 downTo 0).map { DateBuckets.addDays(today, -it) }
    val byDay = records.groupBy { DateBuckets.startOfDay(it.timestamp) }
    return starts.map { start ->
        val dayRecords = byDay[start].orEmpty()
        DayBucket(
            startMillis = start,
            reps = dayRecords.size,
            greenReps = dayRecords.count { it.feedbackColor == DepthFeedback.GREEN },
            valgusReps = dayRecords.count { it.kneeValgus }
        )
    }
}
