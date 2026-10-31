package com.example.fichuga.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


@Dao
interface NotesDao {

    @Query("""
        UPDATE semesters
        SET weekOneStartDate = :date
        WHERE id = :semesterId
        AND weekOneStartDate = ''
    """)
    suspend fun initializeWeekOneStartDate(
        semesterId: Long,
        date: String
    )

    // Семестры

    @Query("SELECT * FROM semesters ORDER BY id DESC")
    fun getAllSemesters(): Flow<List<SemesterEntity>>

    @Insert
    suspend fun addSemester(
        semester: SemesterEntity
    ): Long

    @Delete
    suspend fun deleteSemester(
        semester: SemesterEntity
    )
    @Query("DELETE FROM lesson_slots WHERE semesterId = :semesterId")
    suspend fun deleteLessonSlotsForSemester(semesterId: Long)

    @Query("DELETE FROM lesson_notes WHERE semesterId = :semesterId")
    suspend fun deleteLessonNotesForSemester(semesterId: Long)

    @Query("DELETE FROM subject_notes WHERE semesterId = :semesterId")
    suspend fun deleteSubjectNotesForSemester(semesterId: Long)

    @Transaction
    suspend fun deleteSemesterWithData(semester: SemesterEntity) {
        deleteLessonSlotsForSemester(semester.id)
        deleteLessonNotesForSemester(semester.id)
        deleteSubjectNotesForSemester(semester.id)
        deleteSemester(semester)
    }

    @Query("""
    UPDATE semesters
    SET weekOneStartDate = :date
    WHERE id = :semesterId
""")
    suspend fun updateWeekOneStartDate(
        semesterId: Long,
        date: String
    )

    // Расписание

    @Query(
        """
        SELECT * FROM lesson_slots
        WHERE semesterId = :semesterId
        AND weekNumber = :weekNumber
        ORDER BY dayOfWeek, slotNumber
        """
    )
    fun getLessonSlots(
        semesterId: Long,
        weekNumber: Int
    ): Flow<List<LessonSlotEntity>>

    @Insert
    suspend fun addLessonSlot(
        slot: LessonSlotEntity
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLessonSlot(
        slot: LessonSlotEntity
    )

    @Delete
    suspend fun deleteLessonSlot(
        slot: LessonSlotEntity
    )

    // Очистить расписание одной недели в выбранном семестре
    @Query("""
        DELETE FROM lesson_slots
        WHERE semesterId = :semesterId
        AND weekNumber = :weekNumber
    """)
    suspend fun clearWeek(
        semesterId: Long,
        weekNumber: Int
    )

    // Очистить всё расписание выбранного семестра
    @Query("""
        DELETE FROM lesson_slots
        WHERE semesterId = :semesterId
    """)
    suspend fun clearSemesterSchedule(
        semesterId: Long
    )

    // Заметки к конкретным занятиям


    @Query("""
        SELECT * FROM lesson_notes
        WHERE semesterId = :semesterId
        AND date = :date
        ORDER BY slotNumber
    """)
    fun getLessonNotesForDate(
        semesterId: Long,
        date: String
    ): Flow<List<LessonNoteEntity>>

    @Query("""
        SELECT * FROM lesson_notes
        WHERE semesterId = :semesterId
        AND date = :date
        AND slotNumber = :slotNumber
        LIMIT 1
    """)
    suspend fun getLessonNote(
        semesterId: Long,
        date: String,
        slotNumber: Int
    ): LessonNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLessonNote(note: LessonNoteEntity)

    @Query("""
        DELETE FROM lesson_notes
        WHERE semesterId = :semesterId
        AND date = :date
        AND slotNumber = :slotNumber
    """)
    suspend fun deleteLessonNote(
        semesterId: Long,
        date: String,
        slotNumber: Int
    )



    // Несколько заметок к одному предмету

    @Query(
        """
        SELECT * FROM subject_notes
        WHERE semesterId = :semesterId
        AND subjectName = :subjectName
        ORDER BY createdAt DESC
        """
    )
    fun getSubjectNotes(
        semesterId: Long,
        subjectName: String
    ): Flow<List<SubjectNoteEntity>>

    @Insert
    suspend fun addSubjectNote(
        note: SubjectNoteEntity
    )

    @Delete
    suspend fun deleteSubjectNote(
        note: SubjectNoteEntity
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubjectNote(note: SubjectNoteEntity)

    // Расписание звонков

    @Query("SELECT * FROM bell_times ORDER BY slotNumber")
    fun getAllBellTimes(): Flow<List<BellTimeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBellTime(bellTime: BellTimeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultBellTimes(
        bellTimes: List<BellTimeEntity>
    )
}
