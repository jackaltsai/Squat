package com.heartchen.squat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：補上 dNow / duser / kneeValgusRatio 三個原始值欄位。
 * 全部允許 NULL，v1 既有的紀錄不需要填補預設值，直接 ALTER TABLE 即可。
 * 用真正的 migration 而非 fallbackToDestructiveMigration，是因為封閉測試的測試人員
 * 已經在裝置上累積訓練紀錄，砍掉重建會讓他們的資料憑空消失。
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE squat_rep_records ADD COLUMN dNow REAL")
        db.execSQL("ALTER TABLE squat_rep_records ADD COLUMN duser REAL")
        db.execSQL("ALTER TABLE squat_rep_records ADD COLUMN kneeValgusRatio REAL")
    }
}

@Database(entities = [SquatRepRecord::class], version = 2, exportSchema = false)
@TypeConverters(SquatTypeConverters::class)
abstract class SquatDatabase : RoomDatabase() {
    abstract fun squatRepDao(): SquatRepDao

    companion object {
        @Volatile
        private var instance: SquatDatabase? = null

        fun getInstance(context: Context): SquatDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SquatDatabase::class.java,
                    "squat.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
