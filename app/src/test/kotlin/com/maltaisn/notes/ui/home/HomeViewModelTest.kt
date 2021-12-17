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
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.model.SortSettings
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.note.NoteItemFactory
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel

    private lateinit var labelsRepo: MockLabelsRepository
    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager
    private lateinit var itemFactory: NoteItemFactory

    private val buildTypeBehavior: BuildTypeBehavior = mock()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        labelsRepo = MockLabelsRepository()
        labelsRepo.addLabel(Label(1, "label"))
        labelsRepo.addLabelRefs(listOf(LabelRef(3, 1)))

        notesRepo = MockNotesRepository(labelsRepo)
        notesRepo.addNote(testNote(id = 1, title = "b", status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.PINNED))
        notesRepo.addNote(testNote(id = 2, title = "b", status = NoteStatus.ACTIVE,
            added = dateFor("2100-01-01"), modified = dateFor("2100-01-01")))
        notesRepo.addNote(testNote(id = 5, title = "A", status = NoteStatus.ACTIVE))
        notesRepo.addNote(testNote(id = 3, status = NoteStatus.ARCHIVED))
        notesRepo.addNote(testNote(id = 4, status = NoteStatus.DELETED))

        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
            on { lastTrashReminderTime } doReturn 0
            on { swipeActionLeft } doReturn SwipeAction.DELETE
            on { swipeActionRight } doReturn SwipeAction.ARCHIVE
        }

        itemFactory = NoteItemFactory(prefs)

        viewModel = HomeViewModel(SavedStateHandle(), notesRepo, labelsRepo, prefs,
            ReminderAlarmManager(notesRepo, MockAlarmCallback()), itemFactory, buildTypeBehavior)
    }

    @Test
    fun `should show only active notes (both pinned and unpinned)`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
            assertTrue(viewModel.fabShown.getOrAwaitValue())

            assertEquals(listOf(
                HomeViewModel.PINNED_HEADER_ITEM,
                noteItem(notesRepo.requireNoteById(1)),
                HomeViewModel.NOT_PINNED_HEADER_ITEM,
                noteItem(notesRepo.requireNoteById(2)),
                noteItem(notesRepo.requireNoteById(5))
            ), viewModel.noteItems.getOrAwaitValue())
        }

    @Test
    fun `should show only active notes (pinned only)`() = mainCoroutineRule.runBlockingTest {
        notesRepo.deleteNote(2)
        notesRepo.deleteNote(5)
        val note = notesRepo.requireNoteById(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))

        assertEquals(listOf(
            HomeViewModel.PINNED_HEADER_ITEM,
            noteItem(note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only active notes (unpinned only)`() = mainCoroutineRule.runBlockingTest {
        notesRepo.deleteNote(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))

        assertEquals(listOf(
            noteItem(notesRepo.requireNoteById(2)),
            noteItem(notesRepo.requireNoteById(5)),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only archived notes`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.requireNoteById(3)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ARCHIVED))
        assertFalse(viewModel.fabShown.getOrAwaitValue())

        assertEquals(listOf(
            noteItem(note, listOf(labelsRepo.requireLabelById(1)))
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only deleted notes`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.requireNoteById(4)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        assertFalse(viewModel.fabShown.getOrAwaitValue())

        assertEquals(listOf(
            MessageItem(-1, R.string.trash_reminder_message,
                listOf(PrefsManager.TRASH_AUTO_DELETE_DELAY.inWholeDays)),
            noteItem(note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should update list when data is changed`() = mainCoroutineRule.runBlockingTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))

        notesRepo.insertNote(testNote(status = NoteStatus.ACTIVE))
        val newNote = notesRepo.getNoteById(notesRepo.lastNoteId)!!

        assertEquals(listOf(
            HomeViewModel.PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(1)),
            HomeViewModel.NOT_PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(2)),
            noteItem(newNote),
            noteItem(notesRepo.requireNoteById(5)),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should update list when trash reminder item is dismissed`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
            viewModel.onMessageItemDismissed(MessageItem(-1, 0, emptyList()), 0)

            verify(prefs).lastTrashReminderTime = any()

            assertEquals(listOf(
                noteItem(notesRepo.requireNoteById(4))
            ), viewModel.noteItems.getOrAwaitValue())
        }

    @Test
    fun `should only allow note swipe in active notes`() = mainCoroutineRule.runBlockingTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        assertEquals(SwipeAction.DELETE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(SwipeAction.ARCHIVE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))

        viewModel.setDestination(HomeDestination.Status(NoteStatus.ARCHIVED))
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))

        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))
    }

    @Test
    fun `should not allow swiping if no action set`() = mainCoroutineRule.runBlockingTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        whenever(prefs.swipeActionLeft) doReturn SwipeAction.NONE
        whenever(prefs.swipeActionRight) doReturn SwipeAction.NONE
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(SwipeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))
    }

    @Test
    fun `should change list layout mode`() = mainCoroutineRule.runBlockingTest {
        viewModel.toggleListLayoutMode()

        verify(prefs).listLayoutMode = NoteListLayoutMode.GRID
        assertEquals(NoteListLayoutMode.GRID, viewModel.listLayoutMode.getOrAwaitValue())
    }

    @Test
    fun `should invoke build type behavior`() = mainCoroutineRule.runBlockingTest {
        viewModel.doExtraAction()
        verify(buildTypeBehavior).doExtraAction(viewModel)
    }

    @Test
    fun `should show empty trash confirm`() = mainCoroutineRule.runBlockingTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        viewModel.emptyTrashPre()
        assertLiveDataEventSent(viewModel.showEmptyTrashDialogEvent)
    }

    @Test
    fun `should empty trash`() = mainCoroutineRule.runBlockingTest {
        viewModel.emptyTrash()
        assertTrue(notesRepo.getNotesByStatus(NoteStatus.DELETED).first().isEmpty())
    }

    @Test
    fun `should check selected items when creating them`() = mainCoroutineRule.runBlockingTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
        assertTrue(getNoteItemAt(1).checked)
    }

    @Test
    fun `should archive note on swipe`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.requireNoteById(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteSwiped(1, NoteAdapter.SwipeDirection.RIGHT)

        assertEquals(NoteStatus.ARCHIVED, notesRepo.requireNoteById(1).status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(note), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
    }

    @Test
    fun `should delete note on swipe`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.requireNoteById(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteSwiped(1, NoteAdapter.SwipeDirection.LEFT)

        assertEquals(NoteStatus.DELETED, notesRepo.requireNoteById(1).status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(note), NoteStatus.ACTIVE, NoteStatus.DELETED))
    }

    @Test
    fun `should consider selection as active`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
            viewModel.onNoteItemLongClicked(getNoteItemAt(3), 3)
            assertEquals(NoteViewModel.NoteSelection(1,
                NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as archived`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.setDestination(HomeDestination.Status(NoteStatus.ARCHIVED))
            viewModel.onNoteItemLongClicked(getNoteItemAt(0), 0)
            assertEquals(NoteViewModel.NoteSelection(1,
                NoteStatus.ARCHIVED, PinnedStatus.CANT_PIN, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should consider selection as deleted`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
            viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
            assertEquals(NoteViewModel.NoteSelection(1,
                NoteStatus.DELETED, PinnedStatus.CANT_PIN, false),
                viewModel.currentSelection.getOrAwaitValue())
        }

    @Test
    fun `should update list when sort settings are changed`() = mainCoroutineRule.runBlockingTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))

        notesRepo.sortField = SortField.TITLE
        notesRepo.sortDirection = SortDirection.ASCENDING
        viewModel.changeSort(SortSettings(notesRepo.sortField, notesRepo.sortDirection))

        assertEquals(listOf(
            HomeViewModel.PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(1)),
            HomeViewModel.NOT_PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(5)),
            noteItem(notesRepo.requireNoteById(2)),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    private fun getNoteItemAt(pos: Int) = viewModel.noteItems.getOrAwaitValue()[pos] as NoteItem

    private fun noteItem(note: Note, labels: List<Label> = emptyList()) =
        itemFactory.createItem(note, labels, false)
}
