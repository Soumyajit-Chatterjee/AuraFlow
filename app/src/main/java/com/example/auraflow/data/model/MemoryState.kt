package com.example.auraflow.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_state")
data class MemoryState(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // "IRON", "INK", "SYSTEM"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
