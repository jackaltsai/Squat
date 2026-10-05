package com.heartchen.squat.stats

import com.heartchen.squat.data.SquatRepRecord
import com.heartchen.squat.squat.ExerciseType
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
     * `firstDayOfWeek` 明確指定為星期一，不吃 locale 預設值
     * （zh-TW 的 Calendar 預設是星期日）。
     *
     * ⚠️ M9 之後 `startOfWeek` / `startOfMonth` 已刪除（統計頁只看單日），
     * 所以目前這個設定只影響 [weekdayLabel]。保留明確設定是為了日後若再加回
     * 週/月檢視時不會突然換一套週界線定義。
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

/**
 * 某一天、每個動作各做了幾下。
 *
 * M9 把統計頁改成「六張動作圖 + 圖裡的當日次數」之後，頁面只需要這一個量。
 * 原本的 `TrainingSummary` / `DayBucket` / `buildDailyBuckets`（今日/本週/本月
 * 三個大數字 + 14 天長條圖 + 每日明細）**已刪除** —— 它們算出的達標率與膝內夾比例
 * 在新版頁面上沒有任何地方顯示，留著就是又一個「宣告了但沒人讀取」
 * （這個專案已經因為這個模式故障過五次）。要回頭看歷史版本請查 git。
 *
 * 分組一律在 Kotlin 端做，不用 SQL 的 strftime —— 理由見 [SquatRepDao.recordsSince]。
 */
data class DailyExerciseCounts(
    /** 這一天的午夜（本地時區），由 [DateBuckets.startOfDay] 算出。 */
    val dayStartMillis: Long,
    /** 只放**當天真的有紀錄**的動作；沒做的動作不會出現在這個 map 裡。 */
    val perExercise: Map<ExerciseType, Int>,
) {
    /** 沒做過的動作回傳 0，而不是 null —— 畫面要顯示「0 下」而不是空白。 */
    fun repsOf(type: ExerciseType): Int = perExercise[type] ?: 0

    val totalReps: Int get() = perExercise.values.sum()

    /** 當天有做過的動作種類數。 */
    val activeExercises: Int get() = perExercise.count { it.value > 0 }
}

/**
 * 把一批紀錄按動作分組，數出 [dayStartMillis] 那一天各做了幾下。
 *
 * 會**過濾掉不屬於那一天**的紀錄：呼叫端若用寬一點的區間查詢（或日界線的定義
 * 與查詢條件有出入），不過濾就會把隔天的次數算進來。
 */
fun buildDailyExerciseCounts(
    records: List<SquatRepRecord>,
    dayStartMillis: Long,
): DailyExerciseCounts {
    val counts = records
        .filter { DateBuckets.startOfDay(it.timestamp) == dayStartMillis }
        .groupingBy { it.exerciseType }
        .eachCount()
    return DailyExerciseCounts(dayStartMillis, counts)
}
