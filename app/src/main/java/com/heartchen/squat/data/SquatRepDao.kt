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
}
