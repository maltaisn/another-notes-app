/*
 * Copyright 2021 Nicolas Maltais
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

package com.maltaisn.notes.ui.search

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.NoteViewModel.NoteSelection
import com.maltaisn.notes.ui.note.adapter.HeaderItem
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchViewModelTest {

    private lateinit var viewModel: SearchViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        notesRepo = MockNotesRepository()
        notesRepo.addNote(testNote(id = 1, title = "13", status = NoteStatus.ACTIVE))
        notesRepo.addNote(testNote(id = 2, title = "23", status = NoteStatus.ARCHIVED))
        notesRepo.addNote(testNote(id = 3, title = "12", status = NoteStatus.DELETED))

        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
        }

        viewModel = SearchViewModel(SavedStateHandle(), notesRepo, prefs,
            ReminderAlarmManager(notesRepo, MockAlarmCallback()))
    }

    @Test
    fun `should show nothing if query is blank`() = mainCoroutineRule.runBlockingTest {
        searchNotesAndWait("    ")
        assertTrue(viewModel.noteItems.getOrAwaitValue().isEmpty())
    }

    @Test
    fun `should show search results for query (only active)`() = mainCoroutineRule.runBlockingTest {
        searchNotesAndWait("1")
        assertEquals(listOf(
            NoteItem(1, notesRepo.requireById(1), false, listOf(0..1), emptyList())
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show search results for query (only archived)`() =
        mainCoroutineRule.runBlockingTest {
            searchNotesAndWait("2")
            assertEquals(listOf(
                HeaderItem(-1, R.string.note_location_archived),
                NoteItem(2, notesRepo.requireById(2), false, listOf(0..1), emptyList())
            ), viewModel.noteItems.getOrAwaitValue())
        }

    @Test
    fun `should show search results for query (active + archived)`() =
        mainCoroutineRule.runBlockingTest {
            searchNotesAndWait("3")
            assertEquals(listOf(
                NoteItem(1, notesRepo.requireById(1), false, listOf(1..2), emptyList()),
                HeaderItem(-1, R.string.note_location_archived),
                NoteItem(2, notesRepo.requireById(2), false, listOf(1..2), emptyList())
            ), viewModel.noteItems.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as active (only active selected)`() =
        mainCoroutineRule.runBlockingTest {
            searchNotesAndWait("3")
            viewModel.onNoteItemLongClicked(getNoteItemAt(0), 0)
            assertEquals(NoteSelection(1, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as archived (only archived selected)`() =
        mainCoroutineRule.runBlockingTest {
            searchNotesAndWait("3")
            viewModel.onNoteItemLongClicked(getNoteItemAt(2), 2)
            assertEquals(NoteSelection(1, NoteStatus.ARCHIVED, PinnedStatus.CANT_PIN, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as active (active + archived selected)`() =
        mainCoroutineRule.runBlockingTest {
            searchNotesAndWait("3")
            viewModel.onNoteItemLongClicked(getNoteItemAt(0), 0)
            viewModel.onNoteItemLongClicked(getNoteItemAt(2), 2)
            assertEquals(NoteSelection(2, NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should check selected items when creating them`() = mainCoroutineRule.runBlockingTest {
        searchNotesAndWait("3")
        viewModel.onNoteItemLongClicked(getNoteItemAt(0), 0)
        assertTrue(getNoteItemAt(0).checked)
    }

    private fun searchNotesAndWait(query: String) {
        viewModel.searchNotes(query)
        mainCoroutineRule.advanceTimeBy(1000)
    }

    private fun getNoteItemAt(pos: Int) = viewModel.noteItems.getOrAwaitValue()[pos] as NoteItem
}
