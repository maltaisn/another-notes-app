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

package com.maltaisn.notes.ui.edit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.edit.EditViewModel.DefaultEditableText
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditChipsItem
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.adapter.EditableText
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notesshared.MainCoroutineRule
import com.maltaisn.notesshared.assertNoteEquals
import com.maltaisn.notesshared.dateFor
import com.maltaisn.notesshared.listNote
import com.maltaisn.notesshared.model.MockLabelsRepository
import com.maltaisn.notesshared.model.MockNotesRepository
import com.maltaisn.notesshared.testNote
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditViewModelTest {

    private lateinit var viewModel: EditViewModel
    private lateinit var notesRepo: MockNotesRepository
    private lateinit var labelsRepo: MockLabelsRepository
    private lateinit var alarmCallback: MockAlarmCallback
    private lateinit var prefs: PrefsManager

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
            LabelRef(1, 1),
            LabelRef(2, 1),
            LabelRef(3, 1),
            LabelRef(3, 2),
        ))

        notesRepo = MockNotesRepository(labelsRepo)
        prefs = mock {
            on { shownDateField } doReturn ShownDateField.ADDED
            on { moveCheckedToBottom } doReturn false
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
        notesRepo.addNote(listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", true),
            ListNoteItem("item 3", false),
            ListNoteItem("item 4", true)
        ), id = 7, title = "title", status = NoteStatus.ACTIVE,
            added = dateFor("2020-03-30")))

        // Sample deleted notes
        notesRepo.addNote(testNote(id = 4, title = "title",
            content = "content", status = NoteStatus.DELETED,
            added = dateFor("2020-03-30"), modified = dateFor("2020-03-31")))
        notesRepo.addNote(listNote(listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", true)
        ), id = 5, title = "title", status = NoteStatus.DELETED,
            added = dateFor("2020-03-30")))

        // Sample archived note
        notesRepo.addNote(testNote(id = 6, title = "title",
            content = "content", status = NoteStatus.ARCHIVED,
            added = dateFor("2020-03-30")))

        alarmCallback = MockAlarmCallback()

        viewModel = EditViewModel(notesRepo, labelsRepo, prefs,
            ReminderAlarmManager(notesRepo, alarmCallback), SavedStateHandle())
    }

    @Test
    fun `should create new blank note`() = runTest {
        viewModel.start()

        assertNoteEquals(testNote(title = "", content = ""), notesRepo.lastAddedNote!!)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditTitleItem("".e, true),
            EditContentItem("".e, true)
        ), viewModel.editItems.getOrAwaitValue())

        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(0, 0, false))
    }

    @Test
    fun `should keep editing same new note if started twice`() = runTest {
        viewModel.start()  // new blank note
        val note = notesRepo.lastAddedNote!!
        viewModel.start()
        assertEquals(note, notesRepo.lastAddedNote!!)  // assert note new note added
    }

    @Test
    fun `should create new blank note with initial label`() = runTest {
        viewModel.start(labelId = 1)

        assertEquals(listOf(
            EditTitleItem("".e, true),
            EditContentItem("".e, true),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should create new blank note and change reminder`() = runTest {
        viewModel.start(changeReminder = true)
        assertLiveDataEventSent(viewModel.showReminderDialogEvent, notesRepo.lastNoteId)
    }

    @Test
    fun `should edit existing text note`() = runTest {
        viewModel.start(1)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem("title".e, true),
            EditContentItem("content".e, true),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should edit existing list note`() = runTest {
        viewModel.start(2)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.LIST, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should open existing text note in trash, not editable`() =
        runTest {
            viewModel.start(4)

            assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.DELETED)
            assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.TEXT)

            assertEquals(listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, false),
                EditContentItem("content".e, false)
            ), viewModel.editItems.getOrAwaitValue())
        }

    @Test
    fun `should open existing list note in trash, not editable`() = runTest {
        viewModel.start(5)

        assertEquals(NoteStatus.DELETED, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.LIST, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, false),
            EditItemItem("item 1".e, checked = true, editable = false, 0),
            EditItemItem("item 2".e, checked = true, editable = false, 1)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should save changed text note`() = runTest {
        val oldNote = notesRepo.requireNoteById(1)

        viewModel.start(1)
        (viewModel.editItems.getOrAwaitValue()[1] as EditTitleItem).title.replaceAll("modified")
        viewModel.saveNote()

        assertNoteEquals(testNote(id = 1, title = "modified", content = "content",
            added = oldNote.addedDate, modified = Date()), notesRepo.lastAddedNote!!)
    }

    @Test
    fun `should save changed list note`() = runTest {
        val oldNote = notesRepo.requireNoteById(2)

        viewModel.start(2)

        val firstItem = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        firstItem.checked = false
        firstItem.content.replaceAll("modified item")

        viewModel.saveNote()

        assertNoteEquals(listNote(listOf(
            ListNoteItem("modified item", false),
            ListNoteItem("item 2", false),
        ), title = "title", status = NoteStatus.ACTIVE,
            added = oldNote.addedDate, modified = Date()), notesRepo.lastAddedNote!!)
    }

    @Test
    fun `should not save unchanged note`() = runTest {
        val note = notesRepo.requireNoteById(1)

        viewModel.start(1)
        viewModel.saveNote()

        assertNoteEquals(note, notesRepo.requireNoteById(1))
    }

    @Test
    fun `should discard blank note on exit`() = runTest {
        viewModel.start()
        viewModel.exit()

        assertNull(notesRepo.getNoteById(notesRepo.lastNoteId))
        assertLiveDataEventSent(viewModel.exitEvent)
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.BLANK_NOTE_DISCARDED)
    }

    @Test
    fun `should convert text note to list note`() = runTest {
        viewModel.start(1)
        viewModel.toggleNoteType()

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem("title".e, true),
            EditItemItem("content".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())

        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(2, 7, false))
    }

    @Test
    fun `should convert list note without checked items to text note`() = runTest {
        viewModel.start(3)
        viewModel.toggleNoteType()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditContentItem("- item 1\n- item 2".e, true),
            EditChipsItem(listOf(notesRepo.requireNoteById(3).reminder!!,
                labelsRepo.requireLabelById(1), labelsRepo.requireLabelById(2)))
        ), viewModel.editItems.getOrAwaitValue())

        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(2, 17, false))
    }

    @Test
    fun `should ask user to delete items before converting list note with checked items`() =
        runTest {
            viewModel.start(2)
            viewModel.toggleNoteType()

            assertLiveDataEventSent(viewModel.showRemoveCheckedConfirmEvent)
        }

    @Test
    fun `should convert list note to text note deleting checked items`() = runTest {
        viewModel.start(2)
        viewModel.convertToText(false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditContentItem("- item 2".e, true),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should convert list note to text note keeping checked items`() = runTest {
        viewModel.start(2)
        viewModel.convertToText(true)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditContentItem("- item 1\n- item 2".e, true),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should move active note to archive`() = runTest {
        val oldNote = notesRepo.requireNoteById(1)

        viewModel.start(1)
        viewModel.moveNoteAndExit()

        assertNoteEquals(oldNote.copy(status = NoteStatus.ARCHIVED, pinned = PinnedStatus.CANT_PIN),
            notesRepo.requireNoteById(1))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should move archived note to active`() = runTest {
        val oldNote = notesRepo.requireNoteById(6)

        viewModel.start(6)
        viewModel.moveNoteAndExit()

        assertNoteEquals(oldNote.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED),
            notesRepo.requireNoteById(6))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.ACTIVE))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should move deleted note to active`() = runTest {
        val oldNote = notesRepo.requireNoteById(4)

        viewModel.start(4)
        viewModel.moveNoteAndExit()

        assertNoteEquals(oldNote.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED),
            notesRepo.requireNoteById(4))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.DELETED, NoteStatus.ACTIVE))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should restore deleted text note and allow edit`() = runTest {
        val oldNote = notesRepo.requireNoteById(4)

        viewModel.start(4)
        viewModel.restoreNoteAndEdit()

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.RESTORED_NOTE)
        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditContentItem("content".e, true)
        ), viewModel.editItems.getOrAwaitValue())

        viewModel.saveNote()
        viewModel.exit()
        assertNoteEquals(oldNote.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED),
            notesRepo.requireNoteById(4))
    }

    @Test
    fun `should restore deleted list note and allow edit`() = runTest {
        viewModel.start(5)
        viewModel.restoreNoteAndEdit()

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.RESTORED_NOTE)
        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
            EditItemAddItem
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should copy note`() = runTest {
        viewModel.start(3)
        viewModel.copyNote("untitled", "Copy")

        // should copy labels but not reminder
        val noteCopy = notesRepo.lastAddedNote!!
        assertNoteEquals(listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", false)
        ), id = 3, title = "title - Copy", status = NoteStatus.ACTIVE, pinned = PinnedStatus.PINNED,
            added = Date(), modified = Date()), noteCopy)
        assertEquals(listOf(1L, 2L), labelsRepo.getLabelIdsForNote(noteCopy.id))

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title - Copy".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(notesRepo.requireNoteById(3).reminder!!,
                labelsRepo.requireLabelById(1), labelsRepo.requireLabelById(2))),
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(1, 12, true))

        assertNull(alarmCallback.alarms[noteCopy.id])
    }

    @Test
    fun `should not copy blank note, only change title`() = runTest {
        viewModel.start()
        val lastAdded = notesRepo.lastAddedNote!!

        viewModel.copyNote("untitled", "Copy")

        assertNoteEquals(lastAdded, notesRepo.lastAddedNote!!)
        assertEquals(listOf(
            EditTitleItem("untitled - Copy".e, true),
            EditContentItem("".e, true)
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(0, 15, true))
    }

    @Test
    fun `should share note text`() = runTest {
        val note = notesRepo.requireNoteById(5)
        viewModel.start(5)
        viewModel.shareNote()

        assertLiveDataEventSent(viewModel.shareEvent, ShareData(note.title, note.asText()))
    }

    @Test
    fun `should delete note`() = runTest {
        val oldNote = notesRepo.requireNoteById(6)
        viewModel.start(6)
        viewModel.deleteNote()

        assertNoteEquals(notesRepo.requireNoteById(6), oldNote.copy(status = NoteStatus.DELETED,
            lastModifiedDate = Date()))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.DELETED))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should delete note with reminder`() = runTest {
        alarmCallback.addAlarm(3, 10)
        val oldNote = notesRepo.requireNoteById(3)
        viewModel.start(3)
        viewModel.deleteNote()

        assertNoteEquals(notesRepo.requireNoteById(3), oldNote.copy(status = NoteStatus.DELETED,
            lastModifiedDate = Date(), reminder = null, pinned = PinnedStatus.CANT_PIN))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
            StatusChange(listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.DELETED))
        assertLiveDataEventSent(viewModel.exitEvent)

        assertNull(alarmCallback.alarms[3])
        // should keep labels though
        assertEquals(listOf(1L, 2L), labelsRepo.getLabelIdsForNote(3))
    }

    @Test
    fun `should ask confirmation when deleting deleted note`() = runTest {
        viewModel.start(4)
        viewModel.deleteNote()

        assertLiveDataEventSent(viewModel.showDeleteConfirmEvent)
    }

    @Test
    fun `should delete deleted note forever and exit`() = runTest {
        viewModel.start(4)
        viewModel.deleteNoteForeverAndExit()

        assertNull(notesRepo.getNoteById(4))
        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should uncheck all items in list note`() = runTest {
        viewModel.start(2)
        viewModel.uncheckAllItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should delete checked items in list note`() = runTest {
        viewModel.start(2)
        viewModel.deleteCheckedItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should split list note item on new line`() = runTest {
        viewModel.start(2)

        val item = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        item.content.append("\n")

        viewModel.onNoteItemChanged(2, false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("".e, checked = false, editable = true, 1),
            EditItemItem("item 2".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(3, 0, false))
    }

    @Test
    fun `should split list note item in multiple items on paste`() =
        runTest {
            viewModel.start(2)

            val item = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
            item.content.append("\nnew item first\nnew item second")

            viewModel.onNoteItemChanged(2, true)

            assertEquals(listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1".e, checked = true, editable = true, 0),
                EditItemItem("new item first".e, checked = false, editable = true, 1),
                EditItemItem("new item second".e, checked = false, editable = true, 2),
                EditItemItem("item 2".e, checked = false, editable = true, 3),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ), viewModel.editItems.getOrAwaitValue())
            assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(4, 15, false))
        }

    @Test
    fun `should merge list note item with previous on backspace`() =
        runTest {
            viewModel.start(2)
            viewModel.onNoteItemBackspacePressed(3)

            assertEquals(listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1item 2".e, checked = true, editable = true, 0),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ), viewModel.editItems.getOrAwaitValue())
            assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(2, 6, true))
        }

    @Test
    fun `should do nothing with note first item on backspace`() =
        runTest {
            viewModel.start(2)
            viewModel.onNoteItemBackspacePressed(1)

            assertEquals(listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1".e, checked = true, editable = true, 0),
                EditItemItem("item 2".e, checked = false, editable = true, 1),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ), viewModel.editItems.getOrAwaitValue())
        }

    @Test
    fun `should delete list note item and focus previous`() = runTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(3)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(2, 6, true))
    }

    @Test
    fun `should delete list note item and focus next`() = runTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(2)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(3, 6, true))
    }

    @Test
    fun `should add blank list note item and focus it`() = runTest {
        viewModel.start(2)
        viewModel.onNoteItemAddClicked(4)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemItem("".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
        assertLiveDataEventSent(viewModel.focusEvent,
            EditViewModel.FocusChange(4, 0, false))
    }

    @Test
    fun `should show can't edit message on try to edit in trash`() =
        runTest {
            viewModel.start(5)
            viewModel.onNoteClickedToEdit()

            assertLiveDataEventSent(viewModel.messageEvent, EditMessage.CANT_EDIT_IN_TRASH)
        }

    @Test
    fun `should allow note drag`() = runTest {
        viewModel.start(2)
        assertTrue(viewModel.isNoteDragEnabled)
    }

    @Test
    fun `should prevent note drag on text note`() = runTest {
        viewModel.start(1)
        assertFalse(viewModel.isNoteDragEnabled)
    }

    @Test
    fun `should prevent note drag on deleted note`() = runTest {
        viewModel.start(5)
        assertFalse(viewModel.isNoteDragEnabled)
    }

    @Test
    fun `should swap list items`() = runTest {
        viewModel.start(2)
        viewModel.onNoteItemSwapped(3, 2)
        viewModel.onNoteItemAddClicked(4)  // swap doesn't update live data, force it to update
        viewModel.saveNote()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemItem("item 1".e, checked = true, editable = true, 1),
            EditItemItem("".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())

        assertEquals(listOf(
            ListNoteItem("item 2", false),
            ListNoteItem("item 1", true),
            ListNoteItem("", false),
        ), notesRepo.lastAddedNote!!.listItems)
    }

    @Test
    fun `should set correct pinned status (unpinned)`() = runTest {
        viewModel.start(1)
        assertEquals(PinnedStatus.UNPINNED, viewModel.notePinned.getOrAwaitValue())
    }

    @Test
    fun `should set correct pinned status (pinned)`() = runTest {
        viewModel.start(3)
        assertEquals(PinnedStatus.PINNED, viewModel.notePinned.getOrAwaitValue())
    }

    @Test
    fun `should set correct pinned status (can't pin)`() = runTest {
        viewModel.start(4)
        assertEquals(PinnedStatus.CANT_PIN, viewModel.notePinned.getOrAwaitValue())
    }

    @Test
    fun `should set correct reminder (no reminder)`() = runTest {
        viewModel.start(1)
        assertNull(viewModel.noteReminder.getOrAwaitValue())
    }

    @Test
    fun `should set correct reminder (has reminder)`() = runTest {
        viewModel.start(3)
        assertEquals(notesRepo.requireNoteById(3).reminder,
            viewModel.noteReminder.getOrAwaitValue())
    }

    @Test
    fun `should edit existing text note (modified date field)`() =
        runTest {
            whenever(prefs.shownDateField) doReturn ShownDateField.MODIFIED
            viewModel.start(1)

            assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
            assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

            assertEquals(listOf(
                EditDateItem(dateFor("2019-01-01").time),
                EditTitleItem("title".e, true),
                EditContentItem("content".e, true),
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ), viewModel.editItems.getOrAwaitValue())
        }

    @Test
    fun `should edit existing text note (no date field)`() = runTest {
        whenever(prefs.shownDateField) doReturn ShownDateField.NONE
        viewModel.start(1)

        assertEquals(NoteStatus.ACTIVE, viewModel.noteStatus.getOrAwaitValue())
        assertEquals(NoteType.TEXT, viewModel.noteType.getOrAwaitValue())

        assertEquals(listOf(
            EditTitleItem("title".e, true),
            EditContentItem("content".e, true),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should separate checked and unchecked items (both)`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should separate checked and unchecked items (unchecked only)`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(3)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(notesRepo.requireNoteById(3).reminder!!,
                labelsRepo.requireLabelById(1), labelsRepo.requireLabelById(2))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should separate checked and unchecked items (checked only)`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(5)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, false),
            EditCheckedHeaderItem(2),
            EditItemItem("item 1".e, checked = true, editable = false, 0),
            EditItemItem("item 2".e, checked = true, editable = false, 1),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should remove checked group after deleting last checked item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(5)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should remove checked group after unchecking last checked item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)
        viewModel.onNoteItemCheckChanged(5, false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should add unchecked group after unchecking first item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(5)
        viewModel.restoreNoteAndEdit()  // note 5 is in trash, we want to edit it
        viewModel.onNoteItemCheckChanged(5, false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should add checked group after checking first item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(3)
        viewModel.onNoteItemCheckChanged(2, true)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditChipsItem(listOf(notesRepo.requireNoteById(3).reminder!!,
                labelsRepo.requireLabelById(1), labelsRepo.requireLabelById(2))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should update existing checked group after checking item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)
        viewModel.onNoteItemCheckChanged(2, true)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(3),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
            EditItemItem("item 4".e, checked = true, editable = true, 3),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should update existing checked group after unchecking item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)
        viewModel.onNoteItemCheckChanged(7, false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemItem("item 4".e, checked = false, editable = true, 3),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should update existing checked group after deleting checked item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)
        viewModel.onNoteItemDeleteClicked(7)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
        ), viewModel.editItems.getOrAwaitValue())

        viewModel.onNoteItemDeleteClicked(6)
        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 1),
            EditItemAddItem,
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should keep actual pos order when checking and unchecking`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)

        // Check and uncheck a bunch of items at random
        repeat(20) {
            val items = viewModel.editItems.value!!
            val itemsPos = items.mapIndexedNotNull { i, item -> i.takeIf { item is EditItemItem } }
            val checkPos = itemsPos[Random.nextInt(itemsPos.size)]
            val item = items[checkPos] as EditItemItem
            viewModel.onNoteItemCheckChanged(checkPos, !item.checked)
        }

        // finally uncheck all
        for ((i, item) in viewModel.editItems.value!!.withIndex()) {
            if (item is EditItemItem && item.checked) {
                viewModel.onNoteItemCheckChanged(i, false)
            }
        }

        viewModel.saveNote()
        assertEquals(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", false),
            ListNoteItem("item 4", false),
        ), notesRepo.lastAddedNote!!.listItems)
    }

    @Test
    fun `should add new items in unchecked section`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)
        viewModel.onNoteItemAddClicked(3)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemItem("".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should add new checked items if newlines inserted in checked section`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)

        val item = viewModel.editItems.getOrAwaitValue()[5] as EditItemItem
        item.content.append("\n")

        viewModel.onNoteItemChanged(5, false)

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(2),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("".e, checked = true, editable = true, 1),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should delete checked items in list note (move checked to bottom)`() = runTest {
        viewModel.start(7)
        viewModel.deleteCheckedItems()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 1),
            EditItemAddItem,
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should keep note changes on conversion`() = runTest {
        viewModel.start(1)

        val contentItem = viewModel.editItems.getOrAwaitValue()[2] as EditContentItem
        contentItem.content.replaceAll("modified")

        viewModel.toggleNoteType()

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem("title".e, true),
            EditItemItem("modified".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should keep note changes on reminder change`() = runTest {
        viewModel.start(1)

        val contentItem = viewModel.editItems.getOrAwaitValue()[2] as EditContentItem
        contentItem.content.replaceAll("modified")

        val reminder = Reminder(dateFor("2020-01-01"), null, dateFor("2020-01-01"), 1, false)
        viewModel.onReminderChange(reminder)

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem("title".e, true),
            EditContentItem("modified".e, true),
            EditChipsItem(listOf(reminder, labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    private val String.e: EditableText
        get() = DefaultEditableText(this)
}
