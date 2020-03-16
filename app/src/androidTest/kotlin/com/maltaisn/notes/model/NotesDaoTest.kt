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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00.000Z")
        val note = Note(1, "1", NoteType.TEXT, "note", "content",
                null, time, time, NoteStatus.ACTIVE)

        val id = notesDao.insert(note)
        assertEquals(note, notesDao.getById(id))

        val updatedNote = Note(1, "1", NoteType.TEXT, "note update", "content",
                null, time, time, NoteStatus.ACTIVE)
        notesDao.update(updatedNote)
        assertEquals(updatedNote, notesDao.getById(id))
        assertEquals(updatedNote, notesDao.getByUuid("1"))

        notesDao.delete(updatedNote)
        assertNull(notesDao.getById(id))
        assertNull(notesDao.getIdByUuid("1"))
    }

    @Test
    fun getIdByUuidTest() = runBlocking {
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00.000Z")
        val note = Note(1, "1", NoteType.TEXT, "note", "content",
                null, time, time, NoteStatus.ACTIVE)

        notesDao.insert(note)
        assertEquals(1, notesDao.getIdByUuid("1"))
        assertNull(notesDao.getIdByUuid("0"))
    }

    @Test
    fun getNotesByStatusTest() = runBlocking {
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00.000Z")
        val activeNotes = mutableListOf<Note>()
        var id = 1L
        repeat(5) {
            for (status in NoteStatus.values()) {
                val note = Note(id, id.toString(), NoteType.TEXT,"note",
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
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00.000Z")
        val note = Note(1, "1", NoteType.TEXT, "note", "content", null, time, time, NoteStatus.ACTIVE)
        notesDao.insert(note)
        notesDao.insert(Note(2, "2", NoteType.TEXT, "title", "foo", null, time, time, NoteStatus.ACTIVE))
        notesDao.insert(Note(3, "3", NoteType.TEXT, "my note", "bar", null, time, time, NoteStatus.ACTIVE))

        assertEquals(listOf(note), notesDao.search("content"))
    }

}
