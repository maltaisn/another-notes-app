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

package com.maltaisn.notes.ui.edit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.assertNoteEquals
import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.edit.EditViewModel.DefaultEditableText
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.getOrAwaitValue
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class EditViewModelTest {

    private lateinit var viewModel: EditViewModel
    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()


    @Before
    fun before() {
        notesRepo = MockNotesRepository()
        prefs = mock()

        // Sample active notes
        notesRepo.addNote(testNote(id = 1, title = "title",
                content = "content", status = NoteStatus.ACTIVE,
                added = DateTimeConverter.toDate("2018-01-01T00:00:00.000Z"),
                modified = DateTimeConverter.toDate("2019-01-01T00:00:00.000Z")))
        notesRepo.addNote(listNote(listOf(
                ListNoteItem("item 1", true),
                ListNoteItem("item 2", false)
        ), id = 2, title = "title", status = NoteStatus.ACTIVE))
        notesRepo.addNote(listNote(listOf(
                ListNoteItem("item 1", false),
                ListNoteItem("item 2", false)
        ), id = 3, title = "title", status = NoteStatus.ACTIVE))

        // Sample deleted notes
        notesRepo.addNote(testNote(id = 4, title = "title",
                content = "content", status = NoteStatus.DELETED))
        notesRepo.addNote(listNote(listOf(
                ListNoteItem("item 1", true),
                ListNoteItem("item 2", false)
        ), id = 5, title = "title", status = NoteStatus.DELETED))

        // Sample archived note
        notesRepo.addNote(testNote(id = 6, title = "title",
                content = "content", status = NoteStatus.ARCHIVED))

        viewModel = EditViewModel(notesRepo, prefs)
    }

    @Test
    fun `should create new blank note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(Note.NO_ID)

        assertNoteEquals(notesRepo.lastAddedNote!!,
                testNote(title = "", content = ""))

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.ACTIVE)
        assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.TEXT)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText(""), true),
                EditContentItem(DefaultEditableText(""), true)
        ))
    }

    @Test
    fun `should edit existing text note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.ACTIVE)
        assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.TEXT)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditContentItem(DefaultEditableText("content"), true)
        ))
    }

    @Test
    fun `should edit existing list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.ACTIVE)
        assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.LIST)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
    }

    @Test
    fun `should open existing text note in trash, not editable`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.DELETED)
        assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.TEXT)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), false),
                EditContentItem(DefaultEditableText("content"), false)
        ))
    }

    @Test
    fun `should open existing list note in trash, not editable`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.DELETED)
        assertEquals(viewModel.noteType.getOrAwaitValue(), NoteType.LIST)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), false),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = false),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = false)
        ))
    }

    @Test
    fun `should save changed text note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!

        viewModel.start(1)
        (viewModel.editItems.getOrAwaitValue()[0] as EditTitleItem).title.replaceAll("modified")
        viewModel.save()

        assertNoteEquals(notesRepo.lastAddedNote!!, testNote(id = 1, title = "modified",
                content = "content", added = oldNote.addedDate, modified = Date()))
    }

    @Test
    fun `should save changed list note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(2)!!

        viewModel.start(2)

        val firstItem = viewModel.editItems.getOrAwaitValue()[1] as EditItemItem
        firstItem.checked = false
        firstItem.content.replaceAll("modified item")

        viewModel.save()

        assertNoteEquals(notesRepo.lastAddedNote!!, listNote(listOf(
                ListNoteItem("modified item", false),
                ListNoteItem("item 2", false)
        ), title = "title", status = NoteStatus.ACTIVE,
                added = oldNote.addedDate, modified = Date()))
    }

    @Test
    fun `should not save unchanged note`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.getById(1)!!

        viewModel.start(1)
        viewModel.save()

        assertNoteEquals(notesRepo.getById(1)!!, note)
    }

    @Test
    fun `should discard blank note on exit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(Note.NO_ID)
        viewModel.exit()

        assertNull(notesRepo.getById(notesRepo.lastId))
        assertLiveDataEventSent(viewModel.exitEvent, Unit)
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.BLANK_NOTE_DISCARDED)
    }

    @Test
    fun `should convert text note to list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(1)
        viewModel.toggleNoteType()

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("content"), checked = false, editable = true),
                EditItemAddItem
        ))
    }

    @Test
    fun `should convert list note without checked items to text note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(3)
        viewModel.toggleNoteType()

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditContentItem(DefaultEditableText("- item 1\n- item 2"), true)
        ))
    }

    @Test
    fun `should ask user to delete items before converting list note with checked items`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.toggleNoteType()

        assertLiveDataEventSent(viewModel.showRemoveCheckedConfirmEvent, Unit)
    }

    @Test
    fun `should convert list note to text note deleting checked items`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.convertToText(false)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditContentItem(DefaultEditableText("- item 2"), true)
        ))
    }

    @Test
    fun `should convert list note to text note keeping checked items`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.convertToText(true)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditContentItem(DefaultEditableText("- item 1\n- item 2"), true)
        ))
    }

    @Test
    fun `should move active note to archive`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!

        viewModel.start(1)
        viewModel.moveNoteAndExit()

        assertNoteEquals(notesRepo.getById(1)!!, testNote(
                title = "title", content = "content", status = NoteStatus.ARCHIVED,
                added = DateTimeConverter.toDate("2018-01-01T00:00:00.000Z"),
                modified = Date()))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
                StatusChange(listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.ARCHIVED))
        assertLiveDataEventSent(viewModel.exitEvent, Unit)
    }

    @Test
    fun `should move archived note to active`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(6)!!

        viewModel.start(6)
        viewModel.moveNoteAndExit()

        assertNoteEquals(notesRepo.getById(6)!!, testNote(
                title = "title", content = "content", status = NoteStatus.ACTIVE,
                added = oldNote.addedDate, modified = Date()))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
                StatusChange(listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.ACTIVE))
        assertLiveDataEventSent(viewModel.exitEvent, Unit)
    }

    @Test
    fun `should move deleted note to active`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(4)!!

        viewModel.start(4)
        viewModel.moveNoteAndExit()

        assertNoteEquals(notesRepo.getById(4)!!, testNote(
                title = "title", content = "content", status = NoteStatus.ACTIVE,
                added = oldNote.addedDate, modified = Date()))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
                StatusChange(listOf(oldNote), NoteStatus.DELETED, NoteStatus.ACTIVE))
        assertLiveDataEventSent(viewModel.exitEvent, Unit)
    }

    @Test
    fun `should restore deleted text note and allow edit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        viewModel.restoreNoteAndEdit()

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.ACTIVE)
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.RESTORED_NOTE)
        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditContentItem(DefaultEditableText("content"), true)
        ))
    }

    @Test
    fun `should restore deleted list note and allow edit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(5)
        viewModel.restoreNoteAndEdit()

        assertEquals(viewModel.noteStatus.getOrAwaitValue(), NoteStatus.ACTIVE)
        assertLiveDataEventSent(viewModel.messageEvent, EditMessage.RESTORED_NOTE)
        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
    }

    @Test
    fun `should copy note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(6)
        viewModel.copyNote("untitled", "Copy")

        assertNoteEquals(notesRepo.lastAddedNote!!, testNote(title = "title - Copy",
                content = "content", added = Date(), modified = Date(),
                status = NoteStatus.ARCHIVED))
        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title - Copy"), true),
                EditContentItem(DefaultEditableText("content"), true)
        ))
        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(0, 12, true))
    }

    @Test
    fun `should not copy blank note, only change title`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(Note.NO_ID)
        val lastAdded = notesRepo.lastAddedNote!!

        viewModel.copyNote("untitled", "Copy")

        assertNoteEquals(notesRepo.lastAddedNote!!, lastAdded)
        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("untitled - Copy"), true),
                EditContentItem(DefaultEditableText(""), true)
        ))
        assertLiveDataEventSent(viewModel.focusEvent, EditViewModel.FocusChange(0, 15, true))
    }

    @Test
    fun `should share note text`() = mainCoroutineRule.runBlockingTest {
        val note = notesRepo.getById(5)!!
        viewModel.start(5)
        viewModel.shareNote()

        assertLiveDataEventSent(viewModel.shareEvent, ShareData(note.title, note.asText()))
    }

    @Test
    fun `should delete note`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(6)!!
        viewModel.start(6)
        viewModel.deleteNote()

        assertNoteEquals(notesRepo.getById(6)!!, oldNote.copy(status = NoteStatus.DELETED,
                lastModifiedDate = Date()))
        assertLiveDataEventSent(viewModel.statusChangeEvent,
                StatusChange(listOf(oldNote), NoteStatus.ARCHIVED, NoteStatus.DELETED))
        assertLiveDataEventSent(viewModel.exitEvent, Unit)
    }

    @Test
    fun `should ask confirmation when deleting deleted note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        viewModel.deleteNote()

        assertLiveDataEventSent(viewModel.showDeleteConfirmEvent, Unit)
    }

    @Test
    fun `should delete deleted note forever and exit`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(4)
        viewModel.deleteNoteForeverAndExit()

        assertNull(notesRepo.getById(4))
        assertLiveDataEventSent(viewModel.exitEvent, Unit)
    }

    @Test
    fun `should uncheck all items in list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.uncheckAllItems()

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = false, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
    }

    @Test
    fun `should delete checked items in list note`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.deleteCheckedItems()

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
    }

    @Test
    fun `should split list note item on new line`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)

        val item = viewModel.editItems.getOrAwaitValue()[1] as EditItemItem
        item.content.append("\n")

        viewModel.onNoteItemChanged(item, 1, false)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemItem(DefaultEditableText(""), checked = false, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
        assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(2, 0, false))
    }

    @Test
    fun `should split list note item in multiple items on paste`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)

        val item = viewModel.editItems.getOrAwaitValue()[1] as EditItemItem
        item.content.append("\nnew item first\nnew item second")

        viewModel.onNoteItemChanged(item, 1, true)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemItem(DefaultEditableText("new item first"), checked = false, editable = true),
                EditItemItem(DefaultEditableText("new item second"), checked = false, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
        assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(3, 15, false))
    }

    @Test
    fun `should merge list note item with previous on backspace`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        val item = viewModel.editItems.getOrAwaitValue()[2] as EditItemItem
        viewModel.onNoteItemBackspacePressed(item, 2)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1item 2"), checked = true, editable = true),
                EditItemAddItem
        ))
        assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(1, 6, true))
    }

    @Test
    fun `should do nothing with note first item on backspace`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        val item = viewModel.editItems.getOrAwaitValue()[1] as EditItemItem
        viewModel.onNoteItemBackspacePressed(item, 1)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
    }

    @Test
    fun `should delete list note item and focus previous`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(2)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemAddItem
        ))
        assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(1, 6, true))
    }

    @Test
    fun `should delete list note item and focus next`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemDeleteClicked(1)

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemAddItem
        ))
        assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(2, 6, true))
    }

    @Test
    fun `should add blank list note item and focus it`() = mainCoroutineRule.runBlockingTest {
        viewModel.start(2)
        viewModel.onNoteItemAddClicked()

        assertEquals(viewModel.editItems.getOrAwaitValue(), listOf(
                EditTitleItem(DefaultEditableText("title"), true),
                EditItemItem(DefaultEditableText("item 1"), checked = true, editable = true),
                EditItemItem(DefaultEditableText("item 2"), checked = false, editable = true),
                EditItemItem(DefaultEditableText(""), checked = false, editable = true),
                EditItemAddItem
        ))
        assertLiveDataEventSent(viewModel.focusEvent,
                EditViewModel.FocusChange(3, 0, false))
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
        viewModel.onNoteItemSwapped(2, 1)
        viewModel.save()

        assertEquals(notesRepo.lastAddedNote!!.listItems, listOf(
                ListNoteItem("item 2", false),
                ListNoteItem("item 1", true)
        ))
    }

}
