package com.heartchen.squat.stats

import com.heartchen.squat.data.SquatRepRecord
import com.heartchen.squat.squat.DepthFeedback
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
        feedback: DepthFeedback = DepthFeedback.GREEN,
        valgus: Boolean = false
    ) = SquatRepRecord(
        timestamp = timestamp,
        depthRatio = 1.0f,
        kneeValgus = valgus,
        feedbackColor = feedback,
        mode = TrainingMode.BEGINNER
    )

    @Test
    fun `startOfDay 把時間歸零到當天午夜`() {
        val noon = at(2026, 9, 21, hour = 12, minute = 34)
        val midnight = at(2026, 9, 21, hour = 0, minute = 0)
        assertEquals(midnight, DateBuckets.startOfDay(noon))
    }

    @Test
    fun `startOfWeek 回傳星期一而非星期日`() {
        // 2026-09-21 是星期一；該週的任何一天都應該回推到這天
        val monday = at(2026, 9, 21)
        for (offset in 0..6) {
            val someDay = DateBuckets.addDays(monday, offset)
            val weekStart = DateBuckets.startOfWeek(someDay)
            val cal = Calendar.getInstance().apply { timeInMillis = weekStart }
            assertEquals(
                "第 $offset 天回推的週起始日應為星期一",
                Calendar.MONDAY,
                cal.get(Calendar.DAY_OF_WEEK)
            )
            assertEquals("第 $offset 天應回推到同一個週一", DateBuckets.startOfDay(monday), weekStart)
        }
    }

    @Test
    fun `startOfMonth 回傳當月一號午夜`() {
        val start = DateBuckets.startOfMonth(at(2026, 9, 21, hour = 23))
        assertEquals(at(2026, 9, 1, hour = 0), start)
    }

    @Test
    fun `addDays 能跨月`() {
        assertEquals(at(2026, 10, 1, hour = 0), DateBuckets.addDays(at(2026, 9, 30, hour = 0), 1))
        assertEquals(at(2026, 8, 31, hour = 0), DateBuckets.addDays(at(2026, 9, 1, hour = 0), -1))
    }

    @Test
    fun `buildDailyBuckets 產生固定天數且由舊到新`() {
        val now = at(2026, 9, 21)
        val buckets = buildDailyBuckets(emptyList(), dayCount = 14, now = now)

        assertEquals(14, buckets.size)
        assertEquals("最後一格應為今天", DateBuckets.startOfDay(now), buckets.last().startMillis)
        assertEquals(
            "第一格應為 13 天前",
            DateBuckets.addDays(DateBuckets.startOfDay(now), -13),
            buckets.first().startMillis
        )
        assertTrue("應由舊到新排序", buckets.zipWithNext().all { (a, b) -> a.startMillis < b.startMillis })
    }

    @Test
    fun `沒有訓練的日子也要產生空桶`() {
        val now = at(2026, 9, 21)
        // 只有今天練了 3 下，其餘 13 天空白
        val records = List(3) { record(now) }
        val buckets = buildDailyBuckets(records, dayCount = 14, now = now)

        assertEquals(14, buckets.size)
        assertEquals(3, buckets.last().reps)
        assertTrue("前 13 天應全為 0", buckets.dropLast(1).all { it.reps == 0 })
    }

    @Test
    fun `同一天的紀錄會歸到同一桶，跨日則分開`() {
        val now = at(2026, 9, 21)
        val yesterday = DateBuckets.addDays(now, -1)
        val records = listOf(
            record(at(2026, 9, 21, hour = 0, minute = 1)),   // 今天最早
            record(at(2026, 9, 21, hour = 23, minute = 59)), // 今天最晚
            record(yesterday)
        )
        val buckets = buildDailyBuckets(records, dayCount = 14, now = now)

        assertEquals("今天應有 2 下", 2, buckets.last().reps)
        assertEquals("昨天應有 1 下", 1, buckets[buckets.size - 2].reps)
    }

    @Test
    fun `達標率與膝內夾各自統計`() {
        val now = at(2026, 9, 21)
        val records = listOf(
            record(now, DepthFeedback.GREEN, valgus = false),
            record(now, DepthFeedback.GREEN, valgus = true),
            record(now, DepthFeedback.RED, valgus = false),
            record(now, DepthFeedback.YELLOW, valgus = false)
        )
        val today = buildDailyBuckets(records, dayCount = 7, now = now).last()

        assertEquals(4, today.reps)
        assertEquals(2, today.greenReps)
        assertEquals(1, today.valgusReps)
        assertEquals(50, today.greenRatio)
    }

    @Test
    fun `沒有紀錄時達標率為 0 而不是除以零`() {
        val empty = buildDailyBuckets(emptyList(), dayCount = 7, now = at(2026, 9, 21)).last()
        assertEquals(0, empty.reps)
        assertEquals(0, empty.greenRatio)
    }

    @Test
    fun `視窗外的紀錄不會被算進任何一桶`() {
        val now = at(2026, 9, 21)
        val longAgo = DateBuckets.addDays(now, -30)
        val buckets = buildDailyBuckets(listOf(record(longAgo)), dayCount = 14, now = now)

        assertEquals("30 天前的紀錄不應出現在 14 天的視窗內", 0, buckets.sumOf { it.reps })
    }

    @Test
    fun `摘要的 activeDays 只數有訓練的日子`() {
        val now = at(2026, 9, 21)
        val records = listOf(
            record(now),
            record(DateBuckets.addDays(now, -2)),
            record(DateBuckets.addDays(now, -2))
        )
        val summary = TrainingSummary(
            todayReps = 1,
            weekReps = 3,
            monthReps = 3,
            totalReps = 3,
            days = buildDailyBuckets(records, dayCount = 14, now = now)
        )

        assertEquals("兩天有訓練", 2, summary.activeDays)
        assertEquals("單日最高 2 下", 2, summary.bestDayReps)
    }
}
