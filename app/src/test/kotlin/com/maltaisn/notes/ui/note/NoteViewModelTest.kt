/*
 * Copyright 2023 Nicolas Maltais
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

package com.maltaisn.notes.ui.note

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.NoteViewModel.NoteSelection
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notesshared.MainCoroutineRule
import com.maltaisn.notesshared.assertNoteEquals
import com.maltaisn.notesshared.listNote
import com.maltaisn.notesshared.model.MockLabelsRepository
import com.maltaisn.notesshared.model.MockNotesRepository
import com.maltaisn.notesshared.testNote
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteViewModelTest {

    private lateinit var viewModel: TestNoteViewModel

    private lateinit var labelsRepo: MockLabelsRepository
    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager
    private lateinit var itemFactory: NoteItemFactory

    private lateinit var alarmCallback: MockAlarmCallback

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        labelsRepo = MockLabelsRepository()
        labelsRepo.addLabel(Label(1, "label1"))
        labelsRepo.addLabel(Label(2, "label2"))
        labelsRepo.addLabelRefs(listOf(
            LabelRef(5, 1),
            LabelRef(5, 2),
        ))

        notesRepo = MockNotesRepository(labelsRepo)
        notesRepo.addNote(testNote(id = 1,
            title = "title",
            content = "content",
            status = NoteStatus.ACTIVE,
            added = Date(10),
            modified = Date(12)))
        notesRepo.addNote(testNote(id = 2, status = NoteStatus.ARCHIVED,
            added = Date(10), modified = Date(11)))
        notesRepo.addNote(listNote(listOf(ListNoteItem("item", false)),
            id = 3, status = NoteStatus.DELETED, added = Date(10), modified = Date(10)))
        notesRepo.addNote(testNote(id = 4, status = NoteStatus.ACTIVE,
            added = Date(10), modified = Date(10), pinned = PinnedStatus.PINNED))
        notesRepo.addNote(listNote(listOf(ListNoteItem("item", false)),
            id = 5, status = NoteStatus.ACTIVE, title = "note",
            added = Date(10), modified = Date(10),
            reminder = Reminder(Date(10), null, Date(10), 1, false)))

        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.GRID
        }

        itemFactory = NoteItemFactory(prefs)

        alarmCallback = MockAlarmCallback()

        viewModel = TestNoteViewModel(notesRepo, labelsRepo, prefs,
            ReminderAlarmManager(notesRepo, alarmCallback), itemFactory)
    }

    @Test
    fun `should set saved list layout mode`() = runTest {
        assertEquals(NoteListLayoutMode.GRID, viewModel.listLayoutMode.getOrAwaitValue())
    }

    @Test
    fun `should select all or no notes`() = runTest {
        viewModel.selectAll()

        var listItems = viewModel.noteItems.getOrAwaitValue()
        assertTrue(listItems.all { (it as NoteItem).checked })
        assertEquals(NoteSelection(notesRepo.notesCount,
            NoteStatus.ACTIVE,
            PinnedStatus.UNPINNED,
            true),
            viewModel.currentSelection.getOrAwaitValue())

        viewModel.clearSelection()

        listItems = viewModel.noteItems.getOrAwaitValue()
        assertTrue(listItems.none { (it as NoteItem).checked })
        assertEquals(NoteSelection(0, null, PinnedStatus.CANT_PIN, false),
            viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection on note on long click`() = runTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        assertTrue(viewModel.getNoteItemAt(0).checked)
        assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
            viewModel.currentSelection.getOrAwaitValue())

        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        assertFalse(viewModel.getNoteItemAt(0).checked)
        assertEquals(NoteSelection(0, null, PinnedStatus.CANT_PIN, false),
            viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection on note on click after first selected`() =
        runTest {
            // Select a first note by long click
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)

            viewModel.onNoteItemClicked(viewModel.getNoteItemAt(1), 1)
            assertTrue(viewModel.getNoteItemAt(1).checked)

            viewModel.onNoteItemClicked(viewModel.getNoteItemAt(1), 1)
            assertFalse(viewModel.getNoteItemAt(1).checked)
        }

    @Test
    fun `should send edit event on click`() = runTest {
        // Select a first note by long click
        viewModel.onNoteItemClicked(viewModel.getNoteItemAt(2), 2)

        assertLiveDataEventSent(viewModel.editItemEvent, Pair(3, 2))
    }

    @Test
    fun `should move active note to archive`() = runTest {
        val oldNote = notesRepo.requireNoteById(1)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.moveSelectedNotes()

        assertNoteEquals(oldNote.copy(status = NoteStatus.ARCHIVED, pinned = PinnedStatus.CANT_PIN,
            lastModifiedDate = Date()), notesRepo.requireNoteById(1))
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
    }

    @Test
    fun `should move archived note to active`() = runTest {
        val oldNote = notesRepo.requireNoteById(2)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        viewModel.moveSelectedNotes()

        assertNoteEquals(oldNote.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED,
            lastModifiedDate = Date()), notesRepo.requireNoteById(2))
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.ACTIVE))
    }

    @Test
    fun `should move deleted note to active`() = runTest {
        val oldNote = notesRepo.requireNoteById(3)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(2), 2)
        viewModel.moveSelectedNotes()

        assertNoteEquals(oldNote.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED,
            lastModifiedDate = Date()), notesRepo.requireNoteById(3))
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(oldNote), NoteStatus.DELETED, NoteStatus.ACTIVE))
    }

    @Test
    fun `should delete active note`() = runTest {
        val oldNote = notesRepo.requireNoteById(1)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.deleteSelectedNotesPre()

        assertNoteEquals(oldNote.copy(status = NoteStatus.DELETED, pinned = PinnedStatus.CANT_PIN,
            lastModifiedDate = Date()), notesRepo.requireNoteById(1))
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.DELETED))
    }

    @Test
    fun `should delete active note with reminder`() = runTest {
        alarmCallback.addAlarm(5, 10)
        val oldNote = notesRepo.requireNoteById(5)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(4), 4)
        viewModel.deleteSelectedNotesPre()

        val newNote = notesRepo.requireNoteById(5)
        assertNoteEquals(oldNote.copy(status = NoteStatus.DELETED, pinned = PinnedStatus.CANT_PIN,
            lastModifiedDate = Date(), reminder = null), newNote)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.DELETED))
        assertNull(alarmCallback.alarms[5])
    }

    @Test
    fun `should delete archived note`() = runTest {
        val oldNote = notesRepo.requireNoteById(2)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        viewModel.deleteSelectedNotesPre()

        assertNoteEquals(oldNote.copy(status = NoteStatus.DELETED, lastModifiedDate = Date()),
            notesRepo.requireNoteById(2))
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.DELETED))
    }

    @Test
    fun `should pin and unpin note`() = runTest {
        val oldNote = notesRepo.requireNoteById(1)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.togglePin()
        assertEquals(oldNote.copy(pinned = PinnedStatus.PINNED), notesRepo.requireNoteById(1))

        viewModel.togglePin()
        assertEquals(oldNote, notesRepo.requireNoteById(1))
    }

    @Test
    fun `should ask for confirmation to delete note in trash`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(2), 2)
            viewModel.deleteSelectedNotesPre()

            assertNotNull(notesRepo.getNoteById(3))
            assertLiveDataEventSent(viewModel.showDeleteConfirmEvent)
        }

    @Test
    fun `should delete note in trash directly`() = runTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(2), 2)
        viewModel.deleteSelectedNotes()

        assertNull(notesRepo.getNoteById(3))
    }

    @Test
    fun `should copy selected note`() = runTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(4), 4)
        viewModel.copySelectedNote("untitled", "Copy")

        // should not copy reminder but should copy labels
        assertEquals(listOf(1L, 2L), labelsRepo.getLabelIdsForNote(notesRepo.lastNoteId))
        assertNoteEquals(listNote(listOf(ListNoteItem("item", false)), id = 5,
            status = NoteStatus.ACTIVE, title = "note - Copy",
            added = Date(10), modified = Date(10)),
            notesRepo.lastAddedNote!!)
    }

    @Test
    fun `should share selected note`() = runTest {
        val note = notesRepo.requireNoteById(1)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.shareSelectedNote()

        assertLiveDataEventSent(viewModel.shareEvent, ShareData(note.title, note.asText()))
    }

    @Test
    fun `should update placeholder data`() = runTest {
        assertNull(viewModel.placeholderData.getOrAwaitValue())

        notesRepo.clearAllData()

        assertEquals(TestNoteViewModel.PLACEHOLDER_DATA,
            viewModel.placeholderData.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as pinned (only pinned active)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(3), 3)
            assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.PINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as not pinned (only unpinned active)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
            assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as not pinned (unpinned active + pinned active)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(3), 3)
            assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as can't pin (only archived)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
            assertEquals(NoteSelection(1, NoteStatus.ARCHIVED, PinnedStatus.CANT_PIN, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as not pinned (unpinned active + archived)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
            assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection has a reminder (single item)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(4), 4)
            assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, true),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection has a reminder (multiple items)`() =
        runTest {
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
            viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(4), 4)
            assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, true),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should keep selection after changing note`() = runTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        assertEquals(1, viewModel.currentSelection.getOrAwaitValue().count)
        notesRepo.updateNote(notesRepo.requireNoteById(1).copy(title = "changed note"))
        assertEquals(1, viewModel.currentSelection.getOrAwaitValue().count)
    }

    private class TestNoteViewModel(
        notesRepo: MockNotesRepository,
        labelsRepo: MockLabelsRepository,
        prefs: PrefsManager,
        reminderAlarmManager: ReminderAlarmManager,
        noteItemFactory: NoteItemFactory,
    ) : NoteViewModel(SavedStateHandle(), notesRepo, labelsRepo, prefs, noteItemFactory, reminderAlarmManager) {

        override val selectedNoteStatus: NoteStatus?
            get() = when {
                selectedNotes.isEmpty() -> null
                selectedNotes.size == 1 -> selectedNotes.first().status
                else -> NoteStatus.ACTIVE
            }

        override fun updatePlaceholder() = PLACEHOLDER_DATA

        init {
            viewModelScope.launch {
                notesRepo.getAllNotesWithLabels().collect { notes ->
                    listItems = notes.map { noteWithLabels ->
                        val note = noteWithLabels.note
                        noteItemFactory.createItem(note, noteWithLabels.labels, isNoteSelected(note))
                    }
                }
            }
        }

        fun getNoteItemAt(pos: Int) = listItems[pos] as NoteItem

        companion object {
            val PLACEHOLDER_DATA = PlaceholderData(0, 0)
        }
    }
}
