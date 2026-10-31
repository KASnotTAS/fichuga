package com.example.fichuga.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subject_notes",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["semesterId", "subjectName"])
    ]
)
data class SubjectNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val semesterId: Long,
    val subjectName: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
