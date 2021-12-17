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

package com.maltaisn.notes.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.note.NoteItemFactory
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.recurpicker.Recurrence
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeViewModelRemindersTest {

    private lateinit var viewModel: HomeViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var labelsRepo: MockLabelsRepository
    private lateinit var prefs: PrefsManager
    private lateinit var itemFactory: NoteItemFactory

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        val todayPast = Date(System.currentTimeMillis() - 10000)
        val todayFuture = Date(System.currentTimeMillis() + 10000)
        val upcoming = Date(System.currentTimeMillis() + 86400000)
        val overdue = Date(System.currentTimeMillis() - 86400000)

        labelsRepo = MockLabelsRepository()
        notesRepo = MockNotesRepository(labelsRepo)
        notesRepo.addNote(testNote(id = 1, status = NoteStatus.ACTIVE, pinned = PinnedStatus.PINNED,
            reminder = Reminder(todayPast, null, todayPast, 1, false)))
        notesRepo.addNote(testNote(id = 2, status = NoteStatus.ACTIVE,
            reminder = Reminder(dateFor("2020-01-01"), Recurrence(Recurrence.Period.DAILY),
                todayFuture, 1, false)))
        notesRepo.addNote(testNote(id = 3, status = NoteStatus.ARCHIVED,
            reminder = Reminder(upcoming, null, upcoming, 1, false)))
        notesRepo.addNote(testNote(id = 4, status = NoteStatus.ARCHIVED,
            reminder = Reminder(overdue, null, overdue, 1, false)))

        // never shown
        notesRepo.addNote(testNote(id = 5, status = NoteStatus.ACTIVE,
            reminder = Reminder(overdue, null, overdue, 1, true)))
        notesRepo.addNote(testNote(id = 6, status = NoteStatus.ARCHIVED))

        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
        }

        itemFactory = NoteItemFactory(prefs)

        viewModel = HomeViewModel(SavedStateHandle(), notesRepo, labelsRepo, prefs,
            ReminderAlarmManager(notesRepo, MockAlarmCallback()), itemFactory, mock())
        viewModel.setDestination(HomeDestination.Reminders)
    }

    @Test
    fun `should show all reminders with headers`() =
        mainCoroutineRule.runBlockingTest {
            assertTrue(viewModel.fabShown.getOrAwaitValue())
            assertEquals(listOf(
                HomeViewModel.OVERDUE_HEADER_ITEM,
                noteItem(notesRepo.requireNoteById(4), true),
                noteItem(notesRepo.requireNoteById(1), true),
                HomeViewModel.TODAY_HEADER_ITEM,
                noteItem(notesRepo.requireNoteById(2)),
                HomeViewModel.UPCOMING_HEADER_ITEM,
                noteItem(notesRepo.requireNoteById(3)),
            ), viewModel.noteItems.getOrAwaitValue())
        }

    @Test
    fun `should mark reminder as done on action button click`() =
        mainCoroutineRule.runBlockingTest {
            val note = notesRepo.requireNoteById(4)
            viewModel.onNoteActionButtonClicked(noteItem(note), 1)
            assertEquals(note.copy(reminder = note.reminder?.markAsDone()),
                notesRepo.requireNoteById(4))
        }

    @Test
    fun `should update list when data is changed`() = mainCoroutineRule.runBlockingTest {
        notesRepo.deleteNote(1)
        notesRepo.deleteNote(2)
        notesRepo.deleteNote(3)
        notesRepo.insertNote(testNote(id = 8, status = NoteStatus.ACTIVE,
            reminder = Reminder(dateFor("2020-01-01"), null, dateFor("2020-01-01"), 1, false)))

        assertEquals(listOf(
            HomeViewModel.OVERDUE_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(8), true),
            noteItem(notesRepo.requireNoteById(4), true),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as active (only active selected)`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.onNoteItemLongClicked(getNoteItemAt(4), 4)
            viewModel.onNoteItemLongClicked(getNoteItemAt(6), 6)
            assertEquals(NoteViewModel.NoteSelection(2,
                NoteStatus.ACTIVE, PinnedStatus.UNPINNED, true),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as archived (only archived selected)`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
            assertEquals(NoteViewModel.NoteSelection(1,
                NoteStatus.ARCHIVED, PinnedStatus.CANT_PIN, true),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as active (active + archived selected)`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
            viewModel.onNoteItemLongClicked(getNoteItemAt(4), 4)
            assertEquals(NoteViewModel.NoteSelection(2,
                NoteStatus.ACTIVE, PinnedStatus.UNPINNED, true),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should not allow swipe actions`() = mainCoroutineRule.runBlockingTest {
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))
    }

    // the rest is already tested in NoteViewModelTest

    private fun getNoteItemAt(pos: Int) = viewModel.noteItems.getOrAwaitValue()[pos] as NoteItem

    private fun noteItem(note: Note, showMarkAsDone: Boolean = false) =
        itemFactory.createItem(note, emptyList(), false, showMarkAsDone)
}
