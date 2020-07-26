/*
 * Copyright 2020 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.model

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.maltaisn.notes.model.converter.*
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteFts


@Database(
        entities = [
            Note::class,
            NoteFts::class
        ],
        version = 3)
@TypeConverters(DateTimeConverter::class, NoteTypeConverter::class,
        NoteStatusConverter::class, NoteMetadataConverter::class, PinnedStatusConverter::class)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun notesDao(): NotesDao


    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // By removing the sync feature, some data is now useless.
                // - Deleted notes table
                // - Synced flag on notes
                // - UUID flag on notes (unique ID across devices)
                database.apply {
                    execSQL("DROP TABLE deleted_notes")
                    execSQL("CREATE TABLE notes_temp (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type INTEGER NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, metadata TEXT NOT NULL, added_date INTEGER NOT NULL, modified_date INTEGER NOT NULL, status INTEGER NOT NULL)")
                    execSQL("INSERT INTO notes_temp (id, type, title, content, metadata, added_date, modified_date, status) SELECT id, type, title, content, metadata, added_date, modified_date, status FROM notes")
                    execSQL("DROP TABLE notes")
                    execSQL("ALTER TABLE notes_temp RENAME TO notes")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // - Add pinned column to notes table. 'unpinned' for active notes, 'can't pin' for others.
                database.apply {
                    execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                    execSQL("UPDATE notes SET pinned = 1 WHERE status == 0")
                }
            }
        }

        val ALL_MIGRATIONS = arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3
        )
    }
}
