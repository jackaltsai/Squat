package com.heartchen.squat.data

import androidx.room.TypeConverter
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.TrainingMode

/** Room 無法直接儲存 enum，這裡以 name 字串來回轉換。 */
class SquatTypeConverters {
    @TypeConverter
    fun fromDepthFeedback(value: DepthFeedback): String = value.name

    @TypeConverter
    fun toDepthFeedback(value: String): DepthFeedback = DepthFeedback.valueOf(value)

    @TypeConverter
    fun fromTrainingMode(value: TrainingMode): String = value.name

    @TypeConverter
    fun toTrainingMode(value: String): TrainingMode = TrainingMode.valueOf(value)
}
