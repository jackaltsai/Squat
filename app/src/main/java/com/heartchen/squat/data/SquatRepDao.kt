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
}
