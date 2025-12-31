package com.example.yoursoundscape.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val audioPath: String,
    val imagePath: String?,
    val createdAt: Long,
    val durationSeconds: Int
)
