package com.example.fichuga.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lesson_slots",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = [
                "semesterId",
                "weekNumber",
                "dayOfWeek",
                "slotNumber"
            ],
            unique = true
        )
    ]
)
data class LessonSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val semesterId: Long,

    // 1 или 2
    val weekNumber: Int,

    // 1 = понедельник, 6 = суббота
    val dayOfWeek: Int,

    // Номер пары в расписании
    val slotNumber: Int,

    val subjectName: String,
    val lessonType: String = "",
    val room: String = ""
)
