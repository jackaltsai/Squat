package com.heartchen.squat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SquatRepRecord::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
        }
    }
}
