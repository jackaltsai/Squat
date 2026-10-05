package com.heartchen.squat.stats

import com.heartchen.squat.data.SquatRepRecord
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.ExerciseType
import com.heartchen.squat.squat.TrainingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 訓練統計分組的單元測試。
 *
 * 這些是純 JVM 測試（Calendar 與資料類別都不依賴 Android framework），
 * 可以直接 `./gradlew test` 跑，不需要實機或模擬器 —— 日期邊界是這個功能最容易出錯、
 * 又最難靠手動操作驗證的部分（要等到跨日、跨月、跨年才看得出來）。
 */
class TrainingStatsTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun fixTimeZone() {
        // 固定時區，否則測試結果會隨執行機器的時區改變
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun record(
        timestamp: Long,
        exercise: ExerciseType = ExerciseType.SQUAT,
        feedback: DepthFeedback = DepthFeedback.GREEN,
        valgus: Boolean = false
    ) = SquatRepRecord(
        timestamp = timestamp,
        depthRatio = 1.0f,
        kneeValgus = valgus,
        feedbackColor = feedback,
        mode = TrainingMode.BEGINNER,
        exerciseType = exercise
    )

    @Test
    fun `startOfDay 把時間歸零到當天午夜`() {
        val noon = at(2026, 9, 21, hour = 12, minute = 34)
        val midnight = at(2026, 9, 21, hour = 0, minute = 0)
        assertEquals(midnight, DateBuckets.startOfDay(noon))
    }

    @Test
    fun `addDays 能跨月`() {
        assertEquals(at(2026, 10, 1, hour = 0), DateBuckets.addDays(at(2026, 9, 30, hour = 0), 1))
        assertEquals(at(2026, 8, 31, hour = 0), DateBuckets.addDays(at(2026, 9, 1, hour = 0), -1))
    }

    // ---- M9：某一天各動作做了幾下 ----
    //
    // 舊的 7 條測試覆蓋 buildDailyBuckets / TrainingSummary（今日/本週/本月 +
    // 14 天長條圖 + 每日明細）。M9 把統計頁改成「六張動作圖 + 圖裡的當日次數」，
    // 那套機制整個沒人讀取了，連同測試一起刪 —— 留著一份沒人用的彙總邏輯
    // 與它的測試，只會讓人以為畫面上還有那些數字。要回頭看請查 git。

    @Test
    fun `各動作分別計數`() {
        val day = at(2026, 10, 5)
        val counts = buildDailyExerciseCounts(
            listOf(
                record(day, ExerciseType.SQUAT),
                record(day + 1000, ExerciseType.SQUAT),
                record(day + 2000, ExerciseType.CHAIR_SQUAT),
                record(day + 3000, ExerciseType.HEEL_RAISE),
                record(day + 4000, ExerciseType.HEEL_RAISE),
                record(day + 5000, ExerciseType.HEEL_RAISE),
            ),
            DateBuckets.startOfDay(day)
        )
        assertEquals(2, counts.repsOf(ExerciseType.SQUAT))
        assertEquals(1, counts.repsOf(ExerciseType.CHAIR_SQUAT))
        assertEquals(3, counts.repsOf(ExerciseType.HEEL_RAISE))
        assertEquals(6, counts.totalReps)
        assertEquals(3, counts.activeExercises)
    }

    @Test
    fun `沒做過的動作回傳 0 而不是 null`() {
        // 畫面要在那一格顯示「0 下」—— 隱藏或留白會讓人分不出
        // 「沒做」與「壞了」。
        val day = at(2026, 10, 5)
        val counts = buildDailyExerciseCounts(
            listOf(record(day, ExerciseType.SQUAT)),
            DateBuckets.startOfDay(day)
        )
        ExerciseType.entries.forEach { type ->
            val reps = counts.repsOf(type)
            assertTrue("${type.name} 不該是負數", reps >= 0)
        }
        assertEquals(0, counts.repsOf(ExerciseType.ARM_RAISE))
    }

    @Test
    fun `完全沒有紀錄時總數為 0 而不是爆掉`() {
        val day = DateBuckets.startOfDay(at(2026, 10, 5))
        val counts = buildDailyExerciseCounts(emptyList(), day)
        assertEquals(0, counts.totalReps)
        assertEquals(0, counts.activeExercises)
        assertEquals(0, counts.repsOf(ExerciseType.SQUAT))
        assertEquals(day, counts.dayStartMillis)
    }

    /**
     * 呼叫端用的是 `recordsBetween(午夜, 隔天午夜)`，但那是 SQL 的區間；
     * 日界線的定義在 Kotlin 端（[DateBuckets.startOfDay]）。兩者若有出入，
     * 不過濾就會把隔天的次數算進今天。
     */
    @Test
    fun `不屬於那一天的紀錄會被過濾掉`() {
        val day = at(2026, 10, 5)
        val dayStart = DateBuckets.startOfDay(day)
        val counts = buildDailyExerciseCounts(
            listOf(
                record(at(2026, 10, 4, hour = 23, minute = 59), ExerciseType.SQUAT),
                record(day, ExerciseType.SQUAT),
                record(at(2026, 10, 6, hour = 0, minute = 1), ExerciseType.SQUAT),
            ),
            dayStart
        )
        assertEquals("只有當天那一筆該被算進來", 1, counts.repsOf(ExerciseType.SQUAT))
    }

    @Test
    fun `當天最早與最晚的紀錄都算在同一天`() {
        val dayStart = DateBuckets.startOfDay(at(2026, 10, 5))
        val counts = buildDailyExerciseCounts(
            listOf(
                record(at(2026, 10, 5, hour = 0, minute = 0), ExerciseType.SQUAT),
                record(at(2026, 10, 5, hour = 23, minute = 59), ExerciseType.SQUAT),
            ),
            dayStart
        )
        assertEquals(2, counts.repsOf(ExerciseType.SQUAT))
    }
}
