/*
 * Copyright 2025 Nicolas Maltais
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
import com.maltaisn.notes.R
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.model.DefaultReminderAlarmManager
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.model.SortSettings
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.note.NoteItemFactory
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.StatusChangeAction
import com.maltaisn.notes.ui.note.TrashCleanDelay
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Collections
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
            on { swipeActionLeft } doReturn StatusChangeAction.DELETE
            on { swipeActionRight } doReturn StatusChangeAction.ARCHIVE
            on { trashCleanDelay } doReturn TrashCleanDelay.WEEK
            on { sortField } doAnswer { notesRepo.sortField }
            on { sortDirection } doAnswer { notesRepo.sortDirection }
        }

        itemFactory = NoteItemFactory(prefs)

        viewModel = HomeViewModel(SavedStateHandle(), notesRepo, labelsRepo, prefs,
            DefaultReminderAlarmManager(notesRepo, prefs, MockAlarmCallback()), itemFactory, buildTypeBehavior)
    }

    @Test
    fun `should show only active notes (both pinned and unpinned)`() = runTest {
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
    fun `should show only active notes (pinned only)`() = runTest {
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
    fun `should show only active notes (unpinned only)`() = runTest {
        notesRepo.deleteNote(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))

        assertEquals(listOf(
            noteItem(notesRepo.requireNoteById(2)),
            noteItem(notesRepo.requireNoteById(5)),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only archived notes`() = runTest {
        val note = notesRepo.requireNoteById(3)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ARCHIVED))
        assertFalse(viewModel.fabShown.getOrAwaitValue())

        assertEquals(listOf(
            noteItem(note, listOf(labelsRepo.requireLabelById(1)))
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should show only deleted notes`() = runTest {
        val note = notesRepo.requireNoteById(4)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        assertFalse(viewModel.fabShown.getOrAwaitValue())

        assertEquals(listOf(
            MessageItem(-1, R.plurals.trash_reminder_message, listOf(7)),
            noteItem(note)
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should update list when data is changed`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))

        notesRepo.insertNote(testNote(status = NoteStatus.ACTIVE))

        assertEquals(listOf(
            HomeViewModel.PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(1)),
            HomeViewModel.NOT_PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(2)),
            noteItem(notesRepo.lastAddedNote!!),
            noteItem(notesRepo.requireNoteById(5)),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should update list when trash reminder item is dismissed`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        viewModel.onMessageItemDismissed(MessageItem(-1, 0, emptyList()), 0)

        verify(prefs).lastTrashReminderTime = any()

        assertEquals(listOf(
            noteItem(notesRepo.requireNoteById(4))
        ), viewModel.noteItems.getOrAwaitValue())
    }

    @Test
    fun `should only allow note swipe in active notes`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        assertEquals(StatusChangeAction.DELETE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(StatusChangeAction.ARCHIVE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))

        viewModel.setDestination(HomeDestination.Status(NoteStatus.ARCHIVED))
        assertEquals(StatusChangeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(StatusChangeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))

        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        assertEquals(StatusChangeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(StatusChangeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))
    }

    @Test
    fun `should not allow swiping if no action set`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        whenever(prefs.swipeActionLeft) doReturn StatusChangeAction.NONE
        whenever(prefs.swipeActionRight) doReturn StatusChangeAction.NONE
        assertEquals(StatusChangeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.LEFT))
        assertEquals(StatusChangeAction.NONE, viewModel.getNoteSwipeAction(NoteAdapter.SwipeDirection.RIGHT))
    }

    @Test
    fun `should change list layout mode`() = runTest {
        viewModel.toggleListLayoutMode()

        verify(prefs).listLayoutMode = NoteListLayoutMode.GRID
        assertEquals(NoteListLayoutMode.GRID, viewModel.listLayoutMode.getOrAwaitValue())
    }

    @Test
    fun `should invoke build type behavior`() = runTest {
        viewModel.doExtraAction()
        verify(buildTypeBehavior).doExtraAction(viewModel)
    }

    @Test
    fun `should show empty trash confirm`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        viewModel.emptyTrashPre()
        assertLiveDataEventSent(viewModel.showEmptyTrashDialogEvent)
    }

    @Test
    fun `should empty trash`() = runTest {
        viewModel.emptyTrash()
        assertTrue(notesRepo.getNotesByStatus(NoteStatus.DELETED).first().isEmpty())
    }

    @Test
    fun `should check selected items when creating them`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
        assertTrue(getNoteItemAt(1).checked)
    }

    @Test
    fun `should archive note on swipe`() = runTest {
        val note = notesRepo.requireNoteById(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteSwiped(getNoteItemAt(1), 1, NoteAdapter.SwipeDirection.RIGHT)

        assertEquals(NoteStatus.ARCHIVED, notesRepo.requireNoteById(1).status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(note), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
    }

    @Test
    fun `should delete note on swipe`() = runTest {
        val note = notesRepo.requireNoteById(1)
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteSwiped(getNoteItemAt(1), 1, NoteAdapter.SwipeDirection.LEFT)

        assertEquals(NoteStatus.DELETED, notesRepo.requireNoteById(1).status)
        assertLiveDataEventSent(viewModel.statusChangeEvent, StatusChange(
            listOf(note), NoteStatus.ACTIVE, NoteStatus.DELETED))
    }

    @Test
    fun `should consider selection as active`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        viewModel.onNoteItemLongClicked(getNoteItemAt(3), 3)
        assertEquals(NoteViewModel.NoteSelection(1,
            NoteStatus.ACTIVE, PinnedStatus.UNPINNED, false),
            viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as archived`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ARCHIVED))
        viewModel.onNoteItemLongClicked(getNoteItemAt(0), 0)
        assertEquals(NoteViewModel.NoteSelection(1,
            NoteStatus.ARCHIVED, PinnedStatus.CANT_PIN, false),
            viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should consider selection as deleted`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.DELETED))
        viewModel.onNoteItemLongClicked(getNoteItemAt(1), 1)
        assertEquals(NoteViewModel.NoteSelection(1,
            NoteStatus.DELETED, PinnedStatus.CANT_PIN, false),
            viewModel.currentSelection.getOrAwaitValue())
    }

    @Test
    fun `should update list when sort settings are changed`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        changeSortSettings(SortField.TITLE, SortDirection.ASCENDING)

        assertEquals(listOf(
            HomeViewModel.PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(1)),
            HomeViewModel.NOT_PINNED_HEADER_ITEM,
            noteItem(notesRepo.requireNoteById(5)),
            noteItem(notesRepo.requireNoteById(2)),
        ), viewModel.noteItems.getOrAwaitValue())
    }

    private suspend fun doDragTest(from: Int, to: Int) {
        notesRepo.insertNote(testNote(status = NoteStatus.ACTIVE))  // id == 6
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        changeSortSettings(SortField.CUSTOM)

        val expectedIds = viewModel.noteItems.getOrAwaitValue().mapTo(mutableListOf()) { it.id }

        val checkCanDrag = { predicate: (NoteItem) -> Boolean ->
            for (item in viewModel.noteItems.getOrAwaitValue().filterIsInstance<NoteItem>()) {
                assertEquals(predicate(item), viewModel.canDrag(item))
            }
        }
        checkCanDrag { false }

        // The identity of this item shouldn't change! It's part of the test.
        val item = getNoteItemAt(from)
        viewModel.onNoteItemLongClicked(item, from)
        checkCanDrag { it.id == item.id }

        viewModel.onNoteDragStart()
        assertEquals(0, viewModel.currentSelection.getOrAwaitValue().count)
        assertFalse(item.checked)
        for ((start, end) in (if (from < to) from..to else from downTo to).windowed(2)) {
            // Swap two consecutive items at a time like would happen with the UI.
            Collections.swap(expectedIds, start, end)
            viewModel.onNoteSwapped(item, start, end)
        }
        viewModel.onNoteDragEnd()

        checkCanDrag { false }
        assertEquals(expectedIds, viewModel.noteItems.getOrAwaitValue().map { it.id })
    }

    @Test
    fun `should not drag note`() = runTest {
        doDragTest(3, 3)
    }

    @Test
    fun `should insert dragged note between others`() = runTest {
        doDragTest(3, 4)
    }

    @Test
    fun `should insert dragged note at the start of section`() = runTest {
        doDragTest(5, 3)
    }

    @Test
    fun `should insert dragged note at the end of list`() = runTest {
        doDragTest(4, 5)
    }

    @Test
    fun `should insert dragged note at the end of section`() = runTest {
        notesRepo.updateNote(notesRepo.requireNoteById(2).copy(pinned = PinnedStatus.PINNED))
        doDragTest(1, 2)
    }

    @Test
    fun `should not have same rank after unarchiving`() = runTest {
        viewModel.setDestination(HomeDestination.Status(NoteStatus.ACTIVE))
        changeSortSettings(SortField.CUSTOM)

        assertTrue(notesRepo.requireNoteById(5).rank.prepend() == notesRepo.requireNoteById(3).rank)
        // Dragging note 2 before note 5 should give it a rank equal to archived note 3
        viewModel.onNoteItemLongClicked(getNoteItemAt(4), 4)
        viewModel.onNoteDragStart()
        viewModel.onNoteSwapped(getNoteItemAt(4), 4, 3)
        viewModel.onNoteDragEnd()

        notesRepo.updateNote(notesRepo.requireNoteById(3)
            .copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED))

        val allRank = viewModel.noteItems.getOrAwaitValue().filterIsInstance<NoteItem>().map { it.note.rank }
        assertTrue(allRank.distinct().size == allRank.size)
    }

    private fun changeSortSettings(field: SortField, direction: SortDirection = SortDirection.ASCENDING) {
        notesRepo.sortField = field
        notesRepo.sortDirection = direction
        viewModel.changeSort(SortSettings(field, direction))
    }

    private fun getNoteItemAt(pos: Int) = viewModel.noteItems.getOrAwaitValue()[pos] as NoteItem

    private fun noteItem(note: Note, labels: List<Label> = emptyList()) =
        itemFactory.createItem(note, labels, false)
}
