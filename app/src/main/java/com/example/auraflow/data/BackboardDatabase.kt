package com.example.auraflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.auraflow.data.dao.MemoryDao
import com.example.auraflow.data.model.MemoryState

@Database(entities = [MemoryState::class], version = 1, exportSchema = false)
abstract class BackboardDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: BackboardDatabase? = null

        fun getDatabase(context: Context): BackboardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BackboardDatabase::class.java,
                    "backboard_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
