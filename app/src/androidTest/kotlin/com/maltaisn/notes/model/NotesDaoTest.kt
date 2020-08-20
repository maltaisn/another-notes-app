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
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.testNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
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
    fun readWriteTests() = runBlocking {
        val note = testNote(id = 1, title = "note")

        // Insert
        val id = notesDao.insert(note)
        assertEquals(note, notesDao.getById(id))

        // Update with insert
        val updatedNote0 = testNote(id = 1, title = "updated note 0")
        notesDao.insert(updatedNote0)
        assertEquals(updatedNote0, notesDao.getById(1))

        // Update directly
        val updatedNote1 = testNote(id = 1, title = "updated note 1")
        notesDao.update(updatedNote1)
        assertEquals(updatedNote1, notesDao.getById(id))

        // Delete
        notesDao.delete(updatedNote1)
        assertNull(notesDao.getById(id))

        // Insert all
        val newNotes = listOf(testNote(id = 1), testNote(id = 2))
        notesDao.insertAll(newNotes)

        // Get all
        assertEquals(notesDao.getAll(), newNotes)

        // Delete all
        notesDao.deleteAll(newNotes)
    }

    @Test
    fun getByStatusTest() = runBlocking {
        // Add 5 notes for each status, ordered by descending last modified time. Also add a pinned active note first.
        // Then compare these notes with the result of the database query.
        val baseDate = dateFor("2020-01-01T00:00:00.000Z")
        val activeNotes = mutableListOf<Note>()

        val pinnedNote = testNote(id = 1, status = NoteStatus.ACTIVE, added = baseDate,
            modified = baseDate, pinned = PinnedStatus.PINNED)
        activeNotes += pinnedNote
        notesDao.insert(pinnedNote)

        var id = 2L
        repeat(5) {
            for (status in NoteStatus.values()) {
                val date = Date(baseDate.time - it * 1000)
                val note = testNote(id = id, status = status, added = date, modified = date)
                notesDao.insert(note)
                if (status == NoteStatus.ACTIVE) {
                    activeNotes += note
                }
                id++
            }
        }

        val noteFlow = notesDao.getByStatus(NoteStatus.ACTIVE)
        assertEquals(activeNotes, noteFlow.first())

        // Delete any note to see if flow is updated.
        notesDao.delete(activeNotes.removeAt(activeNotes.indices.random()))
        assertEquals(activeNotes, noteFlow.first())
    }

    @Test
    fun getByStatusAndDateTest() = runBlocking {
        val dates = listOf(
            "2019-01-01T00:00:00.000Z",
            "2019-05-01T00:00:00.000Z",
            "2019-12-31T12:34:56.789Z",
            "2019-12-31T23:59:59.999Z",
            "2020-01-01T00:00:00.000Z",
            "2020-01-02T00:00:00.000Z")
        val notes = dates.mapIndexed { i, dateStr ->
            val date = dateFor(dateStr)
            testNote(id = i + 1L, added = date, modified = date,
                status = NoteStatus.DELETED)
        }
        notesDao.insertAll(notes)

        val queryNotes = notesDao.getByStatusAndDate(NoteStatus.DELETED, dateFor("2020-01-01T00:00:00.000Z"))
        assertEquals(notes.subList(0, 4).toSet(), queryNotes.toSet())
    }

    @Test
    fun searchNotesTest() = runBlocking {
        val note0 = testNote(id = 1, title = "note", content = "content")
        notesDao.insert(note0)
        notesDao.insert(testNote(id = 2, title = "title", content = "foo"))
        notesDao.insert(testNote(id = 3, title = "my note", content = "bar"))

        val noteFlow = notesDao.search("content")
        assertEquals(listOf(note0), noteFlow.first())

        val note1 = testNote(id = 4, title = "note copy", content = "content copy")
        val note2 = testNote(id = 5,
            title = "archived",
            content = "archived content",
            status = NoteStatus.ARCHIVED)
        notesDao.insert(note1)
        notesDao.insert(note2)
        assertEquals(listOf(note1, note0, note2), noteFlow.first())
    }
}
