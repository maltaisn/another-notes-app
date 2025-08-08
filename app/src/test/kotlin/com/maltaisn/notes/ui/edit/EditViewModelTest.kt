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

package com.maltaisn.notes.ui.edit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.assertNoteEquals
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.DefaultReminderAlarmManager
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability.AVAILABLE
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability.HIDDEN
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability.UNAVAILABLE
import com.maltaisn.notes.ui.edit.actions.EditActionsAvailability
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditChipsItem
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTextItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.undo.copy
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.ShownDateField
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.milliseconds

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
            on { editInitialFocus } doReturn EditInitialFocus.TITLE
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

        val editableTextProvider = object : EditableTextProvider {
            override fun create(text: CharSequence): EditableText {
                // This editable text will call onTextChanged when changed.
                return TestEditableText(text, viewModel)
            }
        }

        viewModel = EditViewModel(notesRepo, labelsRepo, prefs,
            DefaultReminderAlarmManager(notesRepo, alarmCallback),
            editableTextProvider, SavedStateHandle())
    }

    @Test
    fun `should create new blank note`() = runTest {
        viewModel.start()

        assertNoteEquals(testNote(title = "", content = ""), notesRepo.lastAddedNote!!)

        assertEquals(EditActionsAvailability(
            undo = UNAVAILABLE,
            redo = UNAVAILABLE,
            convertToList = AVAILABLE,
            reminderAdd = AVAILABLE,
            archive = AVAILABLE,
            delete = AVAILABLE,
            pin = AVAILABLE,
            share = AVAILABLE,
            copy = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

        assertEquals(listOf(
            EditTitleItem("".e, true),
            EditContentItem("".e, true)
        ), viewModel.editItems.getOrAwaitValue())

        assertWasFocused(0, 0, false)
    }

    @Test
    fun `should create new blank note (focus content)`() = runTest {
        whenever(prefs.editInitialFocus) doReturn EditInitialFocus.CONTENT

        viewModel.start()

        assertWasFocused(1, 0, false)
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

        assertEquals(EditActionsAvailability(
            undo = UNAVAILABLE,
            redo = UNAVAILABLE,
            convertToList = AVAILABLE,
            reminderAdd = AVAILABLE,
            archive = AVAILABLE,
            delete = AVAILABLE,
            pin = AVAILABLE,
            share = AVAILABLE,
            copy = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

        assertEquals(NOTE1_ITEMS, viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should edit existing list note`() = runTest {
        viewModel.start(2)

        assertEquals(EditActionsAvailability(
            undo = UNAVAILABLE,
            redo = UNAVAILABLE,
            convertToText = AVAILABLE,
            reminderAdd = AVAILABLE,
            archive = AVAILABLE,
            delete = AVAILABLE,
            pin = AVAILABLE,
            share = AVAILABLE,
            copy = AVAILABLE,
            uncheckAll = AVAILABLE,
            deleteChecked = AVAILABLE,
            sortItems = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

        assertEquals(NOTE2_ITEMS, viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should open existing text note in trash, not editable`() = runTest {
        viewModel.start(4)

        assertEquals(EditActionsAvailability(
            restore = AVAILABLE,
            deleteForever = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, false),
            EditContentItem("content".e, false)
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should open existing list note in trash, not editable`() = runTest {
        viewModel.start(5)

        assertEquals(EditActionsAvailability(
            restore = AVAILABLE,
            deleteForever = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

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
        itemAt<EditTextItem>(1).text.replaceAll("modified")
        viewModel.saveNote()

        assertNoteEquals(testNote(id = 1, title = "modified", content = "content",
            added = oldNote.addedDate, modified = Date()), notesRepo.lastAddedNote!!)
    }

    @Test
    fun `should save changed list note`() = runTest {
        val oldNote = notesRepo.requireNoteById(2)

        viewModel.start(2)

        val firstItem = itemAt<EditItemItem>(2)
        firstItem.checked = false
        firstItem.text.replaceAll("modified item")

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

        assertWasFocused(2, 7, false)
    }

    @Test
    fun `should convert list note without checked items to text note`() = runTest {
        viewModel.start(3)
        viewModel.toggleNoteType()

        assertEquals(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditContentItem("item 1\nitem 2".e, true),
            EditChipsItem(listOf(notesRepo.requireNoteById(3).reminder!!,
                labelsRepo.requireLabelById(1), labelsRepo.requireLabelById(2)))
        ), viewModel.editItems.getOrAwaitValue())

        assertWasFocused(2, 13, false)
    }

    @Test
    fun `should ask user to delete items before converting list note with checked items`() = runTest {
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
            EditContentItem("item 2".e, true),
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
            EditContentItem("item 1\nitem 2".e, true),
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

        assertEquals(EditActionsAvailability(
            undo = UNAVAILABLE,
            redo = UNAVAILABLE,
            convertToList = AVAILABLE,
            reminderAdd = AVAILABLE,
            archive = AVAILABLE,
            delete = AVAILABLE,
            pin = AVAILABLE,
            share = AVAILABLE,
            copy = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

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

        assertEquals(EditActionsAvailability(
            undo = UNAVAILABLE,
            redo = UNAVAILABLE,
            convertToText = AVAILABLE,
            reminderAdd = AVAILABLE,
            archive = AVAILABLE,
            delete = AVAILABLE,
            pin = AVAILABLE,
            share = AVAILABLE,
            copy = AVAILABLE,
            uncheckAll = AVAILABLE,
            deleteChecked = AVAILABLE,
            sortItems = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())

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
        assertWasFocused(1, 12)

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
        assertWasFocused(0, 15)
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
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        )) {
            viewModel.uncheckAllItems()
            assertTrue(itemAt<EditItemItem>(2).shouldUpdate())
        }
    }

    @Test
    fun `should delete checked items in list note`() = runTest {
        viewModel.start(2)
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        )) {
            viewModel.deleteCheckedItems()
        }
    }

    @Test
    fun `should split list note item on new line (single)`() = runTest {
        viewModel.start(2)
        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1".e, checked = true, editable = true, 0),
                EditItemItem("".e, checked = false, editable = true, 1),
                EditItemItem("item 2".e, checked = false, editable = true, 2),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(3, 0, false),
            undoFocus = EditFocusChange(2, 6, true)
        ) {
            itemAt<EditTextItem>(2).text.replace(6, 6, "\n")
        }
    }

    @Test
    fun `should split list note item on new line (multiple)`() = runTest {
        viewModel.start(2)
        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("it".e, checked = true, editable = true, 0),
                EditItemItem("new item first".e, checked = false, editable = true, 1),
                EditItemItem("new item second1".e, checked = false, editable = true, 2),
                EditItemItem("item 2".e, checked = false, editable = true, 3),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(4, 15, false),
            undoFocus = EditFocusChange(2, 2, true)
        ) {
            itemAt<EditTextItem>(2).text.replace(2, 5, "\nnew item first\nnew item second")
        }
    }

    @Test
    fun `should merge list note item with previous on backspace`() = runTest {
        viewModel.start(2)
        doActionTest(
            itemsAfter = listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1item 2".e, checked = true, editable = true, 0),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(2, 6, true),
            undoFocus = EditFocusChange(3, 0, false)
        ) {
            viewModel.onNoteItemBackspacePressed(3)
        }
    }

    @Test
    fun `should focus title on backspace in content note (no date)`() = runTest {
        whenever(prefs.shownDateField) doReturn ShownDateField.NONE

        viewModel.start(1)
        viewModel.onNoteItemBackspacePressed(1)

        assertEquals(listOf(
            EditTitleItem("title".e, true),
            EditContentItem("content".e, editable = true),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
        assertWasFocused(0, 5)
    }

    @Test
    fun `should focus title on backspace in content note (with date)`() = runTest {
        viewModel.start(1)
        viewModel.onNoteItemBackspacePressed(2)

        assertEquals(NOTE1_ITEMS, viewModel.editItems.getOrAwaitValue())
        assertWasFocused(1, 5)
    }

    @Test
    fun `should focus title on backspace in first list item`() = runTest {
        viewModel.start(2)
        viewModel.onNoteItemBackspacePressed(2)

        assertEquals(NOTE2_ITEMS, viewModel.editItems.getOrAwaitValue())
        assertWasFocused(1, 5)
    }

    @Test
    fun `should delete list note item and focus previous`() = runTest {
        viewModel.start(2)
        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1".e, checked = true, editable = true, 0),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(2, 6, true),
            undoFocus = EditFocusChange(3, 6, false)
        ) {
            viewModel.onNoteItemDeleteClicked(3)
        }
    }

    @Test
    fun `should delete list note item and focus next`() = runTest {
        viewModel.start(2)
        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 2".e, checked = false, editable = true, 0),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(3, 6, true),
            undoFocus = EditFocusChange(2, 6, false),
        ) {
            viewModel.onNoteItemDeleteClicked(2)
        }
    }

    @Test
    fun `should add blank list note item and focus it`() = runTest {
        viewModel.start(2)
        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 1".e, checked = true, editable = true, 0),
                EditItemItem("item 2".e, checked = false, editable = true, 1),
                EditItemItem("".e, checked = false, editable = true, 2),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(4, 0, false),
            undoFocus = EditFocusChange(3, 6, true),
        ) {
            viewModel.onNoteItemAddClicked()
        }
    }

    @Test
    fun `should show can't edit message on try to edit in trash`() = runTest {
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
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemItem("item 1".e, checked = true, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        )) {
            viewModel.onNoteItemSwapped(3, 2)
        }
    }

    @Test
    fun `should set correct pinned status (unpinned)`() = runTest {
        viewModel.start(1)

        val visibility = viewModel.editActionsAvailability.getOrAwaitValue()
        assertEquals(AVAILABLE, visibility.pin)
        assertEquals(HIDDEN, visibility.unpin)
    }

    @Test
    fun `should set correct pinned status (pinned)`() = runTest {
        viewModel.start(3)

        val visibility = viewModel.editActionsAvailability.getOrAwaitValue()
        assertEquals(AVAILABLE, visibility.unpin)
        assertEquals(HIDDEN, visibility.pin)
    }

    @Test
    fun `should set correct pinned status (can't pin)`() = runTest {
        viewModel.start(4)

        val visibility = viewModel.editActionsAvailability.getOrAwaitValue()
        assertEquals(HIDDEN, visibility.unpin)
        assertEquals(HIDDEN, visibility.pin)
    }

    @Test
    fun `should set correct reminder (no reminder)`() = runTest {
        viewModel.start(1)

        val visibility = viewModel.editActionsAvailability.getOrAwaitValue()
        assertEquals(HIDDEN, visibility.reminderEdit)
        assertEquals(AVAILABLE, visibility.reminderAdd)
    }

    @Test
    fun `should set correct reminder (has reminder)`() = runTest {
        viewModel.start(3)

        val visibility = viewModel.editActionsAvailability.getOrAwaitValue()
        assertEquals(AVAILABLE, visibility.reminderEdit)
        assertEquals(HIDDEN, visibility.reminderAdd)
    }

    @Test
    fun `should edit existing text note (modified date field)`() = runTest {
        whenever(prefs.shownDateField) doReturn ShownDateField.MODIFIED
        viewModel.start(1)

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
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 0),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        )) {
            viewModel.onNoteItemDeleteClicked(5)
        }
    }

    @Test
    fun `should remove checked group after unchecking last checked item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        )) {
            viewModel.onNoteItemCheckChanged(5, false)
            assertFalse(itemAt<EditItemItem>(2).shouldUpdate())
        }

        assertTrue(itemAt<EditItemItem>(2).shouldUpdate())
    }

    @Test
    fun `should add unchecked group after unchecking first item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(5)
        viewModel.restoreNoteAndEdit()  // note 5 is in trash, we want to edit it

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
        )) {
            viewModel.onNoteItemCheckChanged(5, false)
        }
    }

    @Test
    fun `should add checked group after checking first item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(3)

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditChipsItem(listOf(notesRepo.requireNoteById(3).reminder!!,
                labelsRepo.requireLabelById(1), labelsRepo.requireLabelById(2))),
        )) {
            viewModel.onNoteItemCheckChanged(2, true)
        }
    }

    @Test
    fun `should update existing checked group after checking item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(3),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
            EditItemItem("item 4".e, checked = true, editable = true, 3),
        )) {
            viewModel.onNoteItemCheckChanged(2, true)
        }
    }

    @Test
    fun `should update existing checked group after unchecking item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemItem("item 4".e, checked = false, editable = true, 3),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
        )) {
            viewModel.onNoteItemCheckChanged(7, false)
        }
    }

    @Test
    fun `should update existing checked group after deleting checked item`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
        )) {
            viewModel.onNoteItemDeleteClicked(7)
        }

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 1),
            EditItemAddItem,
        )) {
            viewModel.onNoteItemDeleteClicked(6)
        }
    }

    @Test
    fun `should keep actual pos order when checking and unchecking`() = runTest {
        for (moveCheckedToBottom in listOf(false, true)) {
            whenever(prefs.moveCheckedToBottom) doReturn moveCheckedToBottom
            viewModel.start(7)

            // Check and uncheck a bunch of items at random
            val rng = Random(0)
            repeat(20) {
                val items = viewModel.editItems.value!!
                val itemsPos = items.mapIndexedNotNull { i, item -> i.takeIf { item is EditItemItem } }
                val checkPos = itemsPos[rng.nextInt(itemsPos.size)]
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
    }

    @Test
    fun `should add new items in unchecked section`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemItem("".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(1),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
        )) {
            viewModel.onNoteItemAddClicked()
        }
    }

    @Test
    fun `should add new checked items if newlines inserted in checked section`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(2)
        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("item 2".e, checked = false, editable = true, 2),
                EditItemAddItem,
                EditCheckedHeaderItem(2),
                EditItemItem("item 1".e, checked = true, editable = true, 0),
                EditItemItem("".e, checked = true, editable = true, 1),
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(6, 0, false),
            undoFocus = EditFocusChange(5, 6, true)
        ) {
            itemAt<EditTextItem>(5).text.replace(6, 6, "\n")
        }
    }

    @Test
    fun `should delete checked items in list note (move checked to bottom)`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 1),
            EditItemAddItem,
        )) {
            viewModel.deleteCheckedItems()
        }
    }

    @Test
    fun `should sort items in list note`() = runTest {
        notesRepo.addNote(listNote(listOf(
            ListNoteItem("xZ", false),
            ListNoteItem("éponge", true),
            ListNoteItem("Ev", false),
            ListNoteItem("xyz", true)
        ), id = 10, title = "title", status = NoteStatus.ACTIVE,
            added = dateFor("2020-03-30")))

        viewModel.start(10)
        itemAt<EditTextItem>(4).text.replaceAll(" Ev")
        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("éponge".e, checked = true, editable = true, 0),
            EditItemItem(" Ev".e, checked = false, editable = true, 1),
            EditItemItem("xyz".e, checked = true, editable = true, 2),
            EditItemItem("xZ".e, checked = false, editable = true, 3),
            EditItemAddItem,
        )) {
            viewModel.sortItems()
        }
    }

    @Test
    fun `should sort items in list note (move checked to bottom)`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true
        viewModel.start(7)
        viewModel.onNoteItemSwapped(2, 3)
        viewModel.onNoteItemSwapped(6, 7)

        doActionTest(listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, 0),
            EditItemItem("item 3".e, checked = false, editable = true, 2),
            EditItemAddItem,
            EditCheckedHeaderItem(2),
            EditItemItem("item 2".e, checked = true, editable = true, 1),
            EditItemItem("item 4".e, checked = true, editable = true, 3),
        )) {
            viewModel.sortItems()
        }
    }

    @Test
    fun `should keep note changes on conversion`() = runTest {
        viewModel.start(1)
        itemAt<EditTextItem>(2).text.replaceAll("modified")

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
        itemAt<EditTextItem>(2).text.replaceAll("modified")

        val reminder = Reminder(dateFor("2020-01-01"), null, dateFor("2020-01-01"), 1, false)
        viewModel.onReminderChange(reminder)

        assertEquals(listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem("title".e, true),
            EditContentItem("modified".e, true),
            EditChipsItem(listOf(reminder, labelsRepo.requireLabelById(1))),
        ), viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should hide list action items if none checked`() = runTest {
        viewModel.start(3)

        assertEquals(EditActionsAvailability(
            undo = UNAVAILABLE,
            redo = UNAVAILABLE,
            convertToText = AVAILABLE,
            reminderEdit = AVAILABLE,
            archive = AVAILABLE,
            delete = AVAILABLE,
            unpin = AVAILABLE,
            share = AVAILABLE,
            copy = AVAILABLE,
            sortItems = AVAILABLE,
        ), viewModel.editActionsAvailability.getOrAwaitValue())
    }

    @Test
    fun `should add list item on enter in title if none exist`() = runTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(2)
        viewModel.onNoteItemDeleteClicked(2)

        doActionTest(
            listOf(
                EditDateItem(dateFor("2020-03-30").time),
                EditTitleItem("title".e, true),
                EditItemItem("".e, checked = false, editable = true, 0),
                EditItemAddItem,
                EditChipsItem(listOf(labelsRepo.requireLabelById(1))),
            ),
            redoFocus = EditFocusChange(2, 0, false),
        ) {
            viewModel.onNoteTitleEnterPressed()
        }
    }

    @Test
    fun `should not add list item on enter in title if any exist`() = runTest {
        viewModel.start(2)
        viewModel.onNoteTitleEnterPressed()

        assertEquals(NOTE2_ITEMS, viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should not do anything on enter in text note title`() = runTest {
        viewModel.start(1)
        viewModel.onNoteTitleEnterPressed()

        assertEquals(NOTE1_ITEMS, viewModel.editItems.getOrAwaitValue())
    }

    @Test
    fun `should undo and redo text change`() = runTest {
        viewModel.start(1)

        val item = itemAt<EditTextItem>(1)
        item.text.replace(2, 5, "ananmen square") // Will I get blacklisted in China??
        assertUndoRedoStatus(canUndo = true)

        viewModel.undo()
        assertWasFocused(1, 4)
        assertEquals("title", item.text.text.toString())
        assertUndoRedoStatus(canRedo = true)

        viewModel.redo()
        assertWasFocused(1, 15)
        assertEquals("tiananmen square", item.text.text.toString())
        assertUndoRedoStatus(canUndo = true)
    }

    @Test
    fun `should batch undo actions`() = runTest {
        viewModel.start(1)

        val item = itemAt<EditTextItem>(2)
        item.text.replace(0, 7, "a")
        item.text.replace(1, 1, "b")
        advanceTimeBy(EditViewModel.UNDO_TEXT_DEBOUNCE_DELAY + 100.milliseconds)
        item.text.replace(2, 2, "c")

        viewModel.undo()
        assertWasFocused(2, 2)
        assertEquals("ab", item.text.text.toString())
        viewModel.undo()
        assertWasFocused(2, 7)
        assertEquals("content", item.text.text.toString())
    }

    private fun TestScope.doActionTest(
        itemsAfter: List<EditListItem>,
        redoFocus: EditFocusChange? = null,
        undoFocus: EditFocusChange? = null,
        action: TestScope.() -> Unit
    ) {
        val itemsBefore = viewModel.editItems.getOrAwaitValue().copy()
        val canUndoBefore = viewModel.editActionsAvailability.getOrAwaitValue().undo == AVAILABLE

        // Make sure next actions are not batched
        advanceTimeBy(EditViewModel.UNDO_TEXT_DEBOUNCE_DELAY + 100.milliseconds)

        action()
        assertUndoRedoStatus(canUndo = true)
        assertEquals(itemsAfter, viewModel.editItems.getOrAwaitValue())
        if (redoFocus != null) {
            assertLiveDataEventSent(viewModel.focusEvent, redoFocus)
        }

        viewModel.undo()
        assertUndoRedoStatus(canRedo = true, canUndo = canUndoBefore)
        assertEquals(itemsBefore, viewModel.editItems.getOrAwaitValue())
        if (undoFocus != null) {
            assertLiveDataEventSent(viewModel.focusEvent, undoFocus)
        }

        viewModel.redo()
        assertUndoRedoStatus(canUndo = true)
        assertEquals(itemsAfter, viewModel.editItems.getOrAwaitValue())
        if (redoFocus != null) {
            assertLiveDataEventSent(viewModel.focusEvent, redoFocus)
        }
    }

    private inline fun <reified T> itemAt(pos: Int): T {
        val item = viewModel.editItems.value!!.getOrNull(pos)
        checkNotNull(item) { "No item at pos $pos" }
        return item as T
    }

    private fun assertWasFocused(itemPos: Int, pos: Int, itemExists: Boolean = true) {
        assertLiveDataEventSent(viewModel.focusEvent, EditFocusChange(itemPos, pos, itemExists))
    }

    private fun assertUndoRedoStatus(canUndo: Boolean = false, canRedo: Boolean = false) {
        val visibility = viewModel.editActionsAvailability.getOrAwaitValue()
        assertEquals(EditActionAvailability.fromBoolean(available = canUndo), visibility.undo)
        assertEquals(EditActionAvailability.fromBoolean(available = canRedo), visibility.redo)
    }

    companion object {
        private val NOTE1_ITEMS = listOf(
            EditDateItem(dateFor("2018-01-01").time),
            EditTitleItem("title".e, true),
            EditContentItem("content".e, editable = true),
            EditChipsItem(listOf(Label(1, "label1"))),
        )

        private val NOTE2_ITEMS = listOf(
            EditDateItem(dateFor("2020-03-30").time),
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, 0),
            EditItemItem("item 2".e, checked = false, editable = true, 1),
            EditItemAddItem,
            EditChipsItem(listOf(Label(1, "label1"))),
        )
    }
}
