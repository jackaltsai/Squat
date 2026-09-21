package com.heartchen.squat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SquatRepDao {
    @Insert
    suspend fun insert(record: SquatRepRecord)

    /** 依時間新到舊排序，供未來的完整歷史紀錄頁面使用。 */
    @Query("SELECT * FROM squat_rep_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SquatRepRecord>>

    /**
     * 一次性讀出全部紀錄（時間舊到新），供「匯出全部歷史紀錄」使用。
     *
     * 畫面上的訓練歷程與 CSV 匯出都只看記憶體裡的本次紀錄，按「重新開始」就會清空；
     * 沒有這支查詢的話，使用者忘記在停止後分享的那一組資料就會永遠留在資料庫裡拿不出來。
     */
    @Query("SELECT * FROM squat_rep_records ORDER BY timestamp ASC")
    suspend fun getAll(): List<SquatRepRecord>

    /**
     * 取某段時間內的紀錄，供訓練統計頁分組。
     *
     * 刻意「不」在 SQL 裡用 strftime + GROUP BY 做日/週/月分組：
     * strftime 的 'localtime' 取的是查詢當下的裝置時區，而摘要數字（今日/本週/本月）
     * 的區間邊界是在 Kotlin 端算的。兩套時區邏輯並存時，跨日跨月的那幾筆會在
     * 「摘要」與「每日明細」之間對不起來。統一由 Kotlin 分組就沒有這個問題。
     */
    @Query("SELECT * FROM squat_rep_records WHERE timestamp >= :from ORDER BY timestamp ASC")
    suspend fun recordsSince(from: Long): List<SquatRepRecord>

    @Query("SELECT COUNT(*) FROM squat_rep_records WHERE timestamp >= :from AND timestamp < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM squat_rep_records")
    suspend fun totalCount(): Int

    @Query("SELECT MIN(timestamp) FROM squat_rep_records")
    suspend fun earliestTimestamp(): Long?
}
