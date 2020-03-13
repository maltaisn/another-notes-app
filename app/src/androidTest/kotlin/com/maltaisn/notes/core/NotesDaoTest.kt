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

package com.maltaisn.notes.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maltaisn.notes.model.NotesDao
import com.maltaisn.notes.model.NotesDatabase
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull


@RunWith(AndroidJUnit4::class)
class NotesDaoTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesDao: NotesDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readWriteTests()  = runBlocking {
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00Z")
        val note = Note(1, NoteType.TEXT, "note", "content",
                null, time, time, NoteStatus.ACTIVE)

        val id = notesDao.insert(note)
        assertEquals(note, notesDao.getById(id))

        val updatedNote = Note(1, NoteType.TEXT, "note update", "content",
                null, time, time, NoteStatus.ACTIVE)
        notesDao.update(updatedNote)
        assertEquals(updatedNote, notesDao.getById(id))

        notesDao.delete(updatedNote)
        assertNull(notesDao.getById(id))
    }

    @Test
    fun getNotesByStatusTest() = runBlocking {
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00Z")
        val activeNotes = mutableListOf<Note>()
        var id = 1L
        repeat(5) {
            for (status in NoteStatus.values()) {
                val note = Note(id, NoteType.TEXT,"note",
                        "content", null, time, time, status)
                notesDao.insert(note)
                if (status == NoteStatus.ACTIVE) {
                    activeNotes += note
                }
                id++
            }
        }

        assertEquals(activeNotes, notesDao.getByStatus(NoteStatus.ACTIVE))
    }

    @Test
    fun searchNotesTest() = runBlocking {
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00Z")
        val note = Note(1, NoteType.TEXT, "note", "content", null, time, time, NoteStatus.ACTIVE)
        notesDao.insert(note)
        notesDao.insert(Note(2, NoteType.TEXT, "title", "foo", null, time, time, NoteStatus.ACTIVE))
        notesDao.insert(Note(3, NoteType.TEXT, "my note", "bar", null, time, time, NoteStatus.ACTIVE))

        assertEquals(listOf(note), notesDao.search("content"))
    }

    @Test
    fun getLastModifiedTest() = runBlocking {
        assertNull(notesDao.getLastModified())

        val time1 = DateTimeConverter.toDate("2020-01-01T00:00:00Z")
        val time2 = DateTimeConverter.toDate("2020-03-01T12:34:56Z")

        val note = Note(10, NoteType.TEXT, "note", "content", null, time2, time2, NoteStatus.ACTIVE)
        notesDao.insert(note)
        notesDao.insert(Note(9, NoteType.TEXT, "title", "foo", null, time1, time2, NoteStatus.ACTIVE))
        notesDao.insert(Note(3, NoteType.TEXT, "my note", "bar", null, time1, time1, NoteStatus.ACTIVE))

        assertEquals(note, notesDao.getLastModified())
    }

}
