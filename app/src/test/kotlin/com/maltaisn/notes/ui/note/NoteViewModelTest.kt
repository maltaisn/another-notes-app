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

package com.maltaisn.notes.ui.note

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.assertNoteEquals
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.NoteViewModel.NoteSelection
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.*


class NoteViewModelTest {

    private lateinit var viewModel: TestNoteViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()


    @Before
    fun before() {
        notesRepo = MockNotesRepository()
        notesRepo.addNote(testNote(id = 1, title = "title", content = "content", status = NoteStatus.ACTIVE,
                added = Date(10), modified = Date(12)))
        notesRepo.addNote(testNote(id = 2, status = NoteStatus.ARCHIVED,
                added = Date(10), modified = Date(11)))
        notesRepo.addNote(testNote(id = 3, status = NoteStatus.DELETED,
                added = Date(10), modified = Date(10)))
        notesRepo.addNote(testNote(id = 4, status = NoteStatus.ACTIVE,
                added = Date(10), modified = Date(10), pinned = PinnedStatus.PINNED))

        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.GRID
        }

        viewModel = TestNoteViewModel(notesRepo, prefs)
    }

    @Test
    fun `should set saved list layout mode`() = mainCoroutineRule.runBlockingTest {
        assertEquals(NoteListLayoutMode.GRID, viewModel.listLayoutMode.getOrAwaitValue())
    }

    @Test
    fun `should select all or no notes`() = mainCoroutineRule.runBlockingTest {
        viewModel.selectAll()

        var listItems = viewModel.noteItems.getOrAwaitValue()
        assertTrue(listItems.all { (it as NoteItem).checked })
        assertEquals(NoteSelection(4, NoteStatus.ACTIVE, PinnedStatus.UNPINNED),
                viewModel.currentSelection.getOrAwaitValue())

        viewModel.clearSelection()

        listItems = viewModel.noteItems.getOrAwaitValue()
        assertTrue(listItems.none { (it as NoteItem).checked })
        assertEquals(NoteSelection(0, null, PinnedStatus.CANT_PIN),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection on note on long click`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        assertTrue(viewModel.getNoteItemAt(0).checked)
        assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.UNPINNED),
                viewModel.currentSelection.getOrAwaitValue())

        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        assertFalse(viewModel.getNoteItemAt(0).checked)
        assertEquals(NoteSelection(0, null, PinnedStatus.CANT_PIN),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection on note on click after first selected`() = mainCoroutineRule.runBlockingTest {
        // Select a first note by long click
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)

        viewModel.onNoteItemClicked(viewModel.getNoteItemAt(1), 1)
        assertTrue(viewModel.getNoteItemAt(1).checked)

        viewModel.onNoteItemClicked(viewModel.getNoteItemAt(1), 1)
        assertFalse(viewModel.getNoteItemAt(1).checked)
    }

    @Test
    fun `should send edit event on click`() = mainCoroutineRule.runBlockingTest {
        // Select a first note by long click
        viewModel.onNoteItemClicked(viewModel.getNoteItemAt(2), 2)

        assertLiveDataEventSent(viewModel.editItemEvent, 3)
    }

    @Test
    fun `should move active note to archive`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.moveSelectedNotes()

        assertEquals(NoteStatus.ARCHIVED, notesRepo.getById(1)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
    }

    @Test
    fun `should move archived note to active`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(2)!!
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        viewModel.moveSelectedNotes()

        assertEquals(NoteStatus.ACTIVE, notesRepo.getById(2)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.ACTIVE))
    }

    @Test
    fun `should move deleted note to active`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(3)!!
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(2), 2)
        viewModel.moveSelectedNotes()

        assertEquals(NoteStatus.ACTIVE, notesRepo.getById(3)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(oldNote), NoteStatus.DELETED, NoteStatus.ACTIVE))
    }

    @Test
    fun `should delete active note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.deleteSelectedNotesPre()

        assertEquals(NoteStatus.DELETED, notesRepo.getById(1)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.DELETED))
    }

    @Test
    fun `should delete archived note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(2)!!
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        viewModel.deleteSelectedNotesPre()

        assertEquals(NoteStatus.DELETED, notesRepo.getById(2)!!.status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
                listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.DELETED))
    }

    @Test
    fun `should ask for confirmation to delete note in trash`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(2), 2)
        viewModel.deleteSelectedNotesPre()

        assertNotNull(notesRepo.getById(3))
        assertLiveDataEventSent(viewModel.showDeleteConfirmEvent, Unit)
    }

    @Test
    fun `should delete note in trash directly`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(2), 2)
        viewModel.deleteSelectedNotes()

        assertNull(notesRepo.getById(3))
    }

    @Test
    fun `should copy selected note`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.copySelectedNote("untitled", "Copy")

        assertNoteEquals(testNote(title = "title - Copy", content = "content",
                type = NoteType.TEXT, status = NoteStatus.ACTIVE,
                added = Date(), modified = Date()), notesRepo.getById(notesRepo.lastId)!!)
    }

    @Test
    fun `should share selected note`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.getById(1)!!
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.shareSelectedNote()

        assertLiveDataEventSent(viewModel.shareEvent, ShareData(note.title, note.asText()))
    }

    @Test
    fun `should update placeholder data`() = mainCoroutineRule.runBlockingTest {
        assertNull(viewModel.placeholderData.getOrAwaitValue())

        notesRepo.clearAllData()

        assertEquals(TestNoteViewModel.PLACEHOLDER_DATA, viewModel.placeholderData.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as pinned (only pinned active)`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(3), 3)
        assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.PINNED),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as not pinned (only unpinned active)`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.UNPINNED),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as not pinned (unpinned active + pinned active)`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(3), 3)
        assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as can't pin (only archived)`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        assertEquals(NoteSelection(1, NoteStatus.ARCHIVED, PinnedStatus.CANT_PIN),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as not pinned (unpinned active + archived)`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(0), 0)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED),
                viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as pinned (pinned active + archived)`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(1), 1)
        viewModel.onNoteItemLongClicked(viewModel.getNoteItemAt(3), 3)
        assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.PINNED),
                viewModel.currentSelection.getOrAwaitValue())
    }

    private class TestNoteViewModel(
            notesRepo: MockNotesRepository,
            prefs: PrefsManager
    ) : NoteViewModel(SavedStateHandle(), notesRepo, prefs) {

        override val selectedNoteStatus: NoteStatus?
            get() = when {
                selectedNotes.isEmpty() -> null
                selectedNotes.size == 1 -> selectedNotes.first().status
                else -> NoteStatus.ACTIVE
            }

        override fun updatePlaceholder() = PLACEHOLDER_DATA

        init {
            viewModelScope.launch {
                notesRepo.getAll().collect { notes ->
                    listItems = notes.map { note ->
                        NoteItem(note.id, note)
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
