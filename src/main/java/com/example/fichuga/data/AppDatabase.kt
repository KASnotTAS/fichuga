
package com.example.fichuga.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SemesterEntity::class,
        LessonSlotEntity::class,
        LessonNoteEntity::class,
        SubjectNoteEntity::class,
        BellTimeEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notesDao(): NotesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем дату начала первой учебной недели.
                db.execSQL(
                    """
                    ALTER TABLE semesters
                    ADD COLUMN weekOneStartDate TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )

                // Старая таблица заметок была привязана к шаблону пары.
                // Новая таблица хранит заметки по конкретным датам.
                db.execSQL("DROP TABLE IF EXISTS lesson_notes")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lesson_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        semesterId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        slotNumber INTEGER NOT NULL,
                        subjectName TEXT NOT NULL,
                        text TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(semesterId)
                            REFERENCES semesters(id)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_lesson_notes_semesterId_date_slotNumber
                    ON lesson_notes (semesterId, date, slotNumber)
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fichuga_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
