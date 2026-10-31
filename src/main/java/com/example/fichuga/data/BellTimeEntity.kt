package com.example.fichuga.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bell_times")
data class BellTimeEntity(
    @PrimaryKey
    val slotNumber: Int,

    val startTime: String,
    val endTime: String
)