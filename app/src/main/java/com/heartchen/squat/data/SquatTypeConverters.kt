package com.heartchen.squat.data

import android.util.Log
import androidx.room.TypeConverter
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.ExerciseType
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

    @TypeConverter
    fun fromExerciseType(value: ExerciseType): String = value.name

    /**
     * 未知名稱一律退回 [ExerciseType.SQUAT] 而不是讓 `valueOf` 丟例外。
     *
     * 動作清單會增刪（例如 2026-09-23 移除了持續型與限時計次型三個動作），
     * 只要某台裝置上曾經寫入過已被移除的名稱，讀取歷史紀錄就會整頁閃退，
     * 而且使用者無從修復。犧牲一筆紀錄的正確分類，好過讓全部紀錄都打不開。
     */
    @TypeConverter
    fun toExerciseType(value: String): ExerciseType =
        runCatching { ExerciseType.valueOf(value) }.getOrElse {
            Log.w("SquatTypeConverters", "Unknown ExerciseType '\$value', falling back to SQUAT")
            ExerciseType.SQUAT
        }
}
