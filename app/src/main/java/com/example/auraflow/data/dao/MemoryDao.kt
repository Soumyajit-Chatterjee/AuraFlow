package com.example.auraflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.auraflow.data.model.MemoryState
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert
    suspend fun insertMemory(memory: MemoryState)

    @Query("SELECT * FROM memory_state ORDER BY timestamp DESC LIMIT 20")
    fun getRecentMemories(): Flow<List<MemoryState>>

    @Query("SELECT * FROM memory_state WHERE category = :category ORDER BY timestamp DESC LIMIT 10")
    fun getRecentMemoriesByCategory(category: String): Flow<List<MemoryState>>
    
    @Query("DELETE FROM memory_state")
    suspend fun clearAllMemories()
}
