package com.heartchen.squat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.TrainingMode

/** M4：每次深蹲（一下）完成時的分析結果紀錄，供訓練歷程頁面與後續量化驗證使用。 */
@Entity(tableName = "squat_rep_records")
data class SquatRepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val depthRatio: Float,
    val kneeValgus: Boolean,
    val feedbackColor: DepthFeedback,
    val mode: TrainingMode
)
