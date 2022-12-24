/*
 * Copyright 2022 Nicolas Maltais
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
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteWithLabels
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.search.SearchQueryCleaner
import com.maltaisn.recurpicker.RecurrenceFinder
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
    private lateinit var labelsDao: LabelsDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
        labelsDao = database.labelsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readWriteTests() = runBlocking {
        val noteNoId = testNote(title = "note")

        // Insert
        val id = notesDao.insert(noteNoId)
        assertEquals(noteNoId.copy(id = id), notesDao.getById(id))

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
        assertEquals(newNotes[0], notesDao.getById(1))
        assertEquals(newNotes[1], notesDao.getById(2))

        // Update all
        val updatedNotes = listOf(testNote(id = 1, title = "updated 1"),
            testNote(id = 2, title = "updated 2"))
        notesDao.updateAll(updatedNotes)
        assertEquals(updatedNotes[0], notesDao.getById(1))
        assertEquals(updatedNotes[1], notesDao.getById(2))

        // Delete all
        notesDao.deleteAll(updatedNotes)
        assertNull(notesDao.getById(1))
        assertNull(notesDao.getById(2))
    }

    @Test
    fun clearTest() = runBlocking {
        notesDao.insertAll(listOf(testNote(), testNote(), testNote()))
        notesDao.clear()
        assertEquals(emptyList(), notesDao.getAll())
    }

    @Test
    fun getByStatusTest() = runBlocking {
        // Add 5 notes for each status, ordered by descending last modified time. Also add a pinned active note first.
        // Then compare these notes with the result of the database query.
        for (i in 1L..5L) {
            labelsDao.insert(Label(i, "label $i"))
        }

        val baseDate = dateFor("2020-01-01T00:00:00.000Z")
        val activeNotes = mutableListOf<NoteWithLabels>()

        val pinnedNote = testNote(id = 1, status = NoteStatus.ACTIVE, added = baseDate,
            modified = baseDate, pinned = PinnedStatus.PINNED)
        activeNotes += NoteWithLabels(pinnedNote, emptyList())
        notesDao.insert(pinnedNote)

        var id = 2L
        repeat(5) {
            for (status in NoteStatus.values()) {
                val date = Date(baseDate.time - it * 1000)
                val note = testNote(id = id, status = status, added = date, modified = date)
                notesDao.insert(note)
                val labelId = it + 1L
                labelsDao.insertRefs(listOf(LabelRef(id, labelId)))
                if (status == NoteStatus.ACTIVE) {
                    activeNotes += NoteWithLabels(note, listOfNotNull(labelsDao.getById(labelId)))
                }
                id++
            }
        }

        val noteFlow = notesDao.getByStatus(NoteStatus.ACTIVE,
            SortSettings(SortField.MODIFIED_DATE, SortDirection.DESCENDING))
        assertEquals(activeNotes, noteFlow.first())

        // Delete any note to see if flow is updated.
        notesDao.delete(activeNotes.removeAt(activeNotes.indices.random()).note)
        assertEquals(activeNotes, noteFlow.first())
    }

    @Test
    fun getByStatusHiddenTest() = runBlocking {
        // If a note has at least one hidden label, it shouldn't be returned by the query.
        val labels = listOf(
            Label(1, "label1", false),
            Label(2, "label2", true),
            Label(3, "label3", true),
        )
        for (label in labels) {
            labelsDao.insert(label)
        }

        val labelIds = listOf(
            listOf(),
            listOf(1L),
            listOf(2L),
            listOf(3L),
            listOf(1L, 2L),
            listOf(2L, 3L),
            listOf(1L, 3L),
            listOf(1L, 2L, 3L),
        )
        val note = testNote(status = NoteStatus.ACTIVE)
        for ((i, ids) in labelIds.withIndex()) {
            val noteId = i + 1L
            notesDao.insert(note.copy(id = noteId))
            labelsDao.insertRefs(ids.map { LabelRef(noteId, it) })
        }

        val notes = notesDao.getByStatus(NoteStatus.ACTIVE,
            SortSettings(SortField.MODIFIED_DATE, SortDirection.DESCENDING))
        assertEquals(listOf(1L, 2L), notes.first().map { it.note.id })
    }

    private suspend fun insertNotesAndLabels(): List<NoteWithLabels> {
        // insert 5 notes, the first note having label1 only,
        // and each note having one more label, so that note 5 has labels 1-5.
        for (i in 1L..5L) {
            labelsDao.insert(Label(i, "label $i"))
        }
        val notes = mutableListOf<NoteWithLabels>()
        for (i in 1L..5L) {
            val date = Date(i * 1000)
            val note = testNote(id = i, added = date, modified = date)
            val labels = (1L..i).map { labelsDao.getById(it)!! }
            notesDao.insert(note)
            labelsDao.insertRefs(labels.map { LabelRef(i, it.id) })
            notes += NoteWithLabels(note, labels)
        }

        return notes
    }

    @Test
    fun getByLabelTest() = runBlocking {
        val notes = insertNotesAndLabels().toMutableList()

        // pin one note to verify correct ordering
        notes[3] = notes[3].copy(notes[3].note.copy(pinned = PinnedStatus.PINNED))
        notesDao.update(notes[3].note)

        val sortSettings = SortSettings(SortField.MODIFIED_DATE, SortDirection.DESCENDING)
        assertEquals(setOf(notes[4]), notesDao.getByLabel(5, sortSettings).first().toSet())
        assertEquals(listOf(notes[3], notes[4], notes[2]), notesDao.getByLabel(3, sortSettings).first())

        val label1Flow = notesDao.getByLabel(1, sortSettings)
        assertEquals(listOf(notes[3], notes[4], notes[2], notes[1], notes[0]), label1Flow.first())

        // verify that label refs are deleted in cascade and that flow is updated
        labelsDao.delete(labelsDao.getById(1)!!)
        assertEquals(emptyList(), label1Flow.first())
    }

    @Test
    fun getAllTest() = runBlocking {
        val notes = insertNotesAndLabels()
        assertEquals(notes.toSet(), notesDao.getAll().toSet())
    }

    @Test
    fun getByIdWithLabelsTest() = runBlocking {
        val notes = insertNotesAndLabels()
        assertEquals(notes[0], notesDao.getByIdWithLabels(1))
        assertEquals(notes[1], notesDao.getByIdWithLabels(2))
        assertEquals(notes[4], notesDao.getByIdWithLabels(5))
    }

    @Test
    fun deleteNotesByStatusAndDateTest() = runBlocking {
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

        notesDao.deleteNotesByStatusAndDate(NoteStatus.DELETED,
            dateFor("2020-01-01T00:00:00.000Z").time)
        assertEquals(setOf(notes[4], notes[5]), notesDao.getAll().map { it.note }.toSet())
    }

    @Test
    fun getAllWithReminderTest() = runBlocking {
        val recurFinder = RecurrenceFinder()
        val reminders = listOf(
            Reminder.create(dateFor("2020-01-01"),
                null, recurFinder),
            null,
            null,
            Reminder.create(dateFor("2020-02-02"),
                null, recurFinder),
            Reminder.create(dateFor("2020-02-02"),
                null, recurFinder).markAsDone(),
        )
        val notes = insertNotesAndLabels().zip(reminders).map { (note, reminder) ->
            val newNote = note.note.copy(reminder = reminder)
            notesDao.update(newNote)
            NoteWithLabels(newNote, note.labels)
        }

        assertEquals(listOf(notes[0], notes[3]), notesDao.getAllWithReminder().first())
    }

    @Test
    fun searchTest() = runBlocking {
        val note0 = testNote(id = 1, title = "note", content = "content")
        notesDao.insert(note0)
        notesDao.insert(testNote(id = 2, title = "title", content = "foo"))
        notesDao.insert(testNote(id = 3, title = "my note", content = "bar"))

        labelsDao.insert(Label(1, "label 1"))
        labelsDao.insertRefs(listOf(LabelRef(1, 1)))

        val noteFlow = notesDao.search("content", SortSettings(SortField.MODIFIED_DATE, SortDirection.DESCENDING))
        assertEquals(listOf(NoteWithLabels(note0, listOf(labelsDao.getById(1)!!))),
            noteFlow.first())

        val note1 = testNote(id = 4, title = "note copy", content = "content copy")
        val note2 = testNote(id = 5,
            title = "archived",
            content = "archived content",
            status = NoteStatus.ARCHIVED)
        notesDao.insert(note1)
        notesDao.insert(note2)
        assertEquals(listOf(note1, note0, note2), noteFlow.first().map { it.note })
    }

    @Test
    fun getByStatusSortTest() = runBlocking {
        // getByStatus should be sorted correctly.
        notesDao.insert(testNote(id = 1, title = "A"))
        notesDao.insert(testNote(id = 2, title = "b"))
        notesDao.insert(testNote(id = 3, title = "C", modified = dateFor("2100-01-01")))

        val notes = listOf(
            notesDao.getByIdWithLabels(1),
            notesDao.getByIdWithLabels(2),
            notesDao.getByIdWithLabels(3),
        )

        val noteFlow = notesDao.getByStatus(NoteStatus.ACTIVE,
            SortSettings(SortField.TITLE, SortDirection.ASCENDING))
        assertEquals(notes, noteFlow.first())
    }

    @Test
    fun getByLabelSortTest() = runBlocking {
        // getByLabel should be sorted correctly.
        labelsDao.insert(Label(1, "label"))
        notesDao.insert(testNote(id = 1, added = dateFor("2000-01-01")))
        notesDao.insert(testNote(id = 2, added = dateFor("2001-01-01")))
        notesDao.insert(testNote(id = 3, added = dateFor("2002-01-01")))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(2, 1),
            LabelRef(3, 1),
        ))

        val notes = listOf(
            notesDao.getByIdWithLabels(1),
            notesDao.getByIdWithLabels(2),
            notesDao.getByIdWithLabels(3),
        )

        val noteFlow = notesDao.getByLabel(1,
            SortSettings(SortField.ADDED_DATE, SortDirection.ASCENDING))
        assertEquals(notes, noteFlow.first())
    }

    @Test
    fun searchSortTest() = runBlocking {
        // search should be sorted correctly.
        notesDao.insert(testNote(id = 1, title = "Aaa", content = "foo"))
        notesDao.insert(testNote(id = 2, title = "bBB", content = "foobar"))
        notesDao.insert(testNote(id = 3, title = "z", content = "foobarbaz"))

        val notes = listOf(
            notesDao.getByIdWithLabels(3),
            notesDao.getByIdWithLabels(2),
            notesDao.getByIdWithLabels(1),
        )

        val noteFlow = notesDao.search(SearchQueryCleaner.clean("foo"),
            SortSettings(SortField.TITLE, SortDirection.DESCENDING))
        assertEquals(notes, noteFlow.first())
    }
}
