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

package com.maltaisn.notes.ui.edit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.assertNoteEquals
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.edit.EditViewModel.DefaultEditableText
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.ShownDateField
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditViewModelTest {

    private lateinit var viewModel: EditViewModel
    private lateinit var notesRepo: MockNotesRepository
    private lateinit var alarmCallback: MockAlarmCallback
    private lateinit var prefs: PrefsManager

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        notesRepo = MockNotesRepository()
        prefs = mock {
            on { shownDateField } doReturn ShownDateField.ADDED
        }

        // Sample active notes
        notesRepo.addNote(testNote(id = 1, title = "title",
            content = "content", status = NoteStatus.ACTIVE,
            added = dateFor("2018-01-01"),
            modified = dateFor("2019-01-01")))
        notesRepo.addNote(listNote(listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", false)
        ), id = 2, title = "title", status = NoteStatus.ACTIVE,
            added = dateFor("2020-03-30")))
        notesRepo.addNote(listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", false)
        ), id = 3, title = "title", status = NoteStatus.ACTIVE, pinned = PinnedStatus.PINNED,
            added = dateFor("2020-03-30"),
            reminder = Reminder(Date(10), null, Date(10), 1, false)))

        // Sample deleted notes
        notesRepo.addNote(testNote(id = 4, title = "title",
            content = "content", status = NoteStatus.DELETED,
            added = dateFor("2020-03-30")))
        notesRepo.addNote(listNote(listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", false)
        ), id = 5, title = "title", status = NoteStatus.DELETED,
            added = dateFor("2020-03-30")))

        // Sample archived note
        notesRepo.addNote(testNote(id = 6, title = "title",
            content = "content", status = NoteStatus.ARCHIVED,
            added = dateFor("2020-03-30")))

        alarmCallback = MockAlarmCallback()

        viewModel = EditViewModel(notesRepo, prefs, ReminderAlarmManager(notesRepo, alarmCallback))
    }

    @Test
    fun `should create new blank note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(Note.NO_ID)

        assertNoteEquals(testNote(title = "", content = ""), notesRepo.lastAddedNote!!)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditTitleItem(DefaultEditableText(""), true),
            EditContentItem(DefaultEditableText(""), true)
        ), viewModel.editItems.getOrAwaitValue())

        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(1, 0, false))
    }

    @Test
    fun `should edit existing text note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("content"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should edit existing list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.LIST, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should open existing text note in trash, not editable`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.DELETED)
        assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.TEXT)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), false),
            EditContentItem(DefaultEditableText("content"), false)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should open existing list note in trash, not editable`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)

        assertEquals(NoteStatus.DELETED, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.LIST, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), false),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = false),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = false)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should save changed text note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.requireById(1)

        viewModel.start(1)
        (viewModel.editItems.getOrAwaitValue()[1] as EditTitleItem).title.replaceAll("modified")
        viewModel.save()

        assertNoteEquals(testNote(id = 1, title = "modified", content = "content",
            added = oldNote.addedDate, modified = Date()), notesRepo.lastAddedNote!!)
    }

    @Test
    fun `should save changed list note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.requireById(2)

        viewModel.start(2)

        val firstItem = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        firstItem.checked = false
        firstItem.content.replaceAll("modified item")

        viewModel.save()

        assertNoteEquals(listNote(listOf(
            ListNoteItem("modified item", false),
            ListNoteItem("item 2", false)
        ), title = "title", status = NoteStatus.ACTIVE,
            added = oldNote.addedDate, modified = Date()), notesRepo.lastAddedNote!!)
    }

    @Test
    fun `should not save unchanged note`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.requireById(1)

        viewModel.start(1)
        viewModel.save()

        assertNoteEquals(note, notesRepo.requireById(1))
    }

    @Test
    fun `should discard blank note on exit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(Note.NO_ID)
        viewModel.exit()

        assertNull(notesRepo.getById(notesRepo.lastId))
        assertLiveDataEventSent(viewModel.exitEvent)
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.BLANK_NOTE_DISCARDED)
    }

    @Test
    fun `should convert text note to list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)
        viewModel.toggleNoteType()

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("content"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should convert list note without checked items to text note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(3)
        viewModel.toggleNoteType()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("- item 1\n- item 2"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should ask user to delete items before converting list note with checked items`() =
        mainCoroutineRule.runBlockingTest {
            viewModel.start(2)
            viewModel.toggleNoteType()

            assertLiveDataEventSent(viewModel.showRemoveCheckedConfirmEvent)
        }

    @Test
    fun `should convert list note to text note deleting checked items`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.convertToText(false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("- item 2"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should convert list note to text note keeping checked items`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.convertToText(true)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("- item 1\n- item 2"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should move active note to archive`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.requireById(1)

        viewModel.start(1)
        viewModel.moveNoteAndExit()

        assertNoteEquals(testNote(
            title = "title", content = "content", status = NoteStatus.ARCHIVED,
            added = dateFor("2018-01-01"),
            modified = Date()), notesRepo.requireById(1))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should move archived note to active`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.requireById(6)

        viewModel.start(6)
        viewModel.moveNoteAndExit()

        assertNoteEquals(testNote(
            title = "title", content = "content", status = NoteStatus.ACTIVE,
            added = oldNote.addedDate, modified = Date()), notesRepo.requireById(6))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.ACTIVE))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should move deleted note to active`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.requireById(4)

        viewModel.start(4)
        viewModel.moveNoteAndExit()

        assertNoteEquals(testNote(
            title = "title", content = "content", status = NoteStatus.ACTIVE,
            added = oldNote.addedDate, modified = Date()), notesRepo.requireById(4))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.DELETED, NoteStatus.ACTIVE))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should restore deleted text note and allow edit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        viewModel.restoreNoteAndEdit()

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.RESTORED_NOTE)
        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("content"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should restore deleted list note and allow edit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)
        viewModel.restoreNoteAndEdit()

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.RESTORED_NOTE)
        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should copy note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(6)
        viewModel.copyNote("untitled", "Copy")

        assertNoteEquals(notesRepo.lastAddedNote!!, testNote(title = "title - Copy",
            content = "content", added = Date(), modified = Date(),
            status = NoteStatus.ARCHIVED))
        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title - Copy"), true),
            EditContentItem(DefaultEditableText("content"), true)
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(1, 12, true))
    }

    @Test
    fun `should not copy blank note, only change title`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(Note.NO_ID)
        val lastAdded = notesRepo.lastAddedNote!!

        viewModel.copyNote("untitled", "Copy")

        assertNoteEquals(lastAdded, notesRepo.lastAddedNote!!)
        assertEquals(listOf(
            EditTitleItem(DefaultEditableText("untitled - Copy"), true),
            EditContentItem(DefaultEditableText(""), true)
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(0, 15, true))
    }

    @Test
    fun `should share note text`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.requireById(5)
        viewModel.start(5)
        viewModel.shareNote()

        assertLiveDataEventSent(viewModel.shareEvent, ShareData(note.title, note.asText()))
    }

    @Test
    fun `should delete note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.requireById(6)
        viewModel.start(6)
        viewModel.deleteNote()

        assertNoteEquals(notesRepo.requireById(6), oldNote.copy(status = NoteStatus.DELETED,
            lastModifiedDate = Date()))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.DELETED))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should delete note with reminder`() = mainCoroutineRule.runBlockingTest {
        alarmCallback.addAlarm(3, 10)
        val oldNote = notesRepo.requireById(3)
        viewModel.start(3)
        viewModel.deleteNote()

        assertNoteEquals(notesRepo.requireById(3), oldNote.copy(status = NoteStatus.DELETED,
            lastModifiedDate = Date(), reminder = null, pinned = PinnedStatus.CANT_PIN))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.DELETED))
        assertLiveDataEventSent(viewModel.exitEvent)
        assertNull(alarmCallback.alarms[3])
    }

    @Test
    fun `should ask confirmation when deleting deleted note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        viewModel.deleteNote()

        assertLiveDataEventSent(viewModel.showDeleteConfirmEvent)
    }

    @Test
    fun `should delete deleted note forever and exit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        viewModel.deleteNoteForeverAndExit()

        assertNull(notesRepo.getById(4))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should uncheck all items in list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.uncheckAllItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = false, editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should not uncheck all items in deleted list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)
        viewModel.uncheckAllItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), false),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = false),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = false)
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.CANT_EDIT_IN_TRASH)
    }

    @Test
    fun `should delete checked items in list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.deleteCheckedItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should not delete checked items in deleted list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)
        viewModel.deleteCheckedItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), false),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = false),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = false)
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.CANT_EDIT_IN_TRASH)
    }

    @Test
    fun `should split list note item on new line`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)

        val item = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        item.content.append("\n")

        viewModel.onNoteItemChanged(item, 2, false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemItem(DefaultEditableText(""), checked = false, editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(3, 0, false))
    }

    @Test
    fun `should split list note item in multiple items on paste`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)

        val item = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        item.content.append("\nnew item first\nnew item second")

        viewModel.onNoteItemChanged(item, 2, true)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemItem(DefaultEditableText("new item first"),
                checked = false,
                editable = true),
            EditItemItem(DefaultEditableText("new item second"),
                checked = false,
                editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(4, 15, false))
    }

    @Test
    fun `should merge list note item with previous on backspace`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        val item = viewModel.editItems.getOrAwaitValue()[3] as EditItemItem
        viewModel.onNoteItemBackspacePressed(item, 3)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1item 2"), checked = true, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(2, 6, true))
    }

    @Test
    fun `should do nothing with note first item on backspace`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        val item = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        viewModel.onNoteItemBackspacePressed(item, 1)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should delete list note item and focus previous`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(3)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(2, 6, true))
    }

    @Test
    fun `should delete list note item and focus next`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(2)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(3, 6, true))
    }

    @Test
    fun `should add blank list note item and focus it`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemAddClicked()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
            EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
            EditItemItem(DefaultEditableText(""), checked = false, editable = true),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(4, 0, false))
    }

    @Test
    fun `should show can't edit message on try to edit in trash`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)
        viewModel.onNoteClickedToEdit()

        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.CANT_EDIT_IN_TRASH)
    }

    @Test
    fun `should allow note drag`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        assertTrue(viewModel.isNoteDragEnabled)
    }

    @Test
    fun `should prevent note drag on text note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)
        assertFalse(viewModel.isNoteDragEnabled)
    }

    @Test
    fun `should prevent note drag on deleted note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)
        assertFalse(viewModel.isNoteDragEnabled)
    }

    @Test
    fun `should swap list items`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemSwapped(3, 2)
        viewModel.save()

        assertEquals(listOf(
            ListNoteItem("item 2", false),
            ListNoteItem("item 1", true)
        ), notesRepo.lastAddedNote!!.listItems)
    }

    @Test
    fun `should set correct pinned status (unpinned)`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)
        assertEquals(PinnedStatus.UNPINNED, viewModel.notePinned.getOrAwaitValue())
    }

    @Test
    fun `should set correct pinned status (pinned)`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(3)
        assertEquals(PinnedStatus.PINNED, viewModel.notePinned.getOrAwaitValue())
    }

    @Test
    fun `should set correct pinned status (can't pin)`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        assertEquals(PinnedStatus.CANT_PIN, viewModel.notePinned.getOrAwaitValue())
    }

    @Test
    fun `should set correct reminder (no reminder)`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)
        assertNull(viewModel.noteReminder.getOrAwaitValue())
    }

    @Test
    fun `should set correct reminder (has reminder)`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(3)
        assertEquals(notesRepo.requireById(3).reminder, viewModel.noteReminder.getOrAwaitValue())
    }

    @Test
    fun `should edit existing text note (modified date field)`() = mainCoroutineRule.runBlockingTest {
        whenever(prefs.shownDateField) doReturn ShownDateField.MODIFIED
        viewModel.start(1)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2019-01-01").time),
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("content"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should edit existing text note (no date field)`() = mainCoroutineRule.runBlockingTest {
        whenever(prefs.shownDateField) doReturn ShownDateField.NONE
        viewModel.start(1)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditTitleItem(DefaultEditableText("title"), true),
            EditContentItem(DefaultEditableText("content"), true)
        ), viewModel.editItems.getOrAwaitValue())
    }


}
