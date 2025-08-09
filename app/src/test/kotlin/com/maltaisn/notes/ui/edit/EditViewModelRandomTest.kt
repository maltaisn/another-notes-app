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
import com.maltaisn.notes.model.DefaultReminderAlarmManager
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.NoteWithLabels
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTextItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.undo.UndoManager
import com.maltaisn.notes.ui.edit.undo.randomString
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.note.ShownDateField
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertTrue

class EditViewModelRandomTest {

    private lateinit var viewModel: EditViewModel
    private lateinit var labelsRepo: MockLabelsRepository
    private lateinit var notesRepo: MockNotesRepository
    private lateinit var prefs: PrefsManager

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    val editItems: List<EditListItem>
        get() = viewModel.editItems.value!!

    @Before
    fun before() {
        labelsRepo = MockLabelsRepository()
        labelsRepo.addLabel(Label(1, "label1"))
        labelsRepo.addLabel(Label(2, "label2"))
        labelsRepo.addLabel(Label(3, "label3"))

        notesRepo = MockNotesRepository(labelsRepo)
        notesRepo.addNote(testNote(id = 1))

        prefs = mock {
            on { shownDateField } doReturn ShownDateField.ADDED
            on { moveCheckedToBottom } doReturn false
            on { editInitialFocus } doReturn EditInitialFocus.TITLE
        }

        val editableTextProvider = object : EditableTextProvider {
            override fun create(text: CharSequence): EditableText {
                // This editable text will call onTextChanged when changed.
                return TestEditableText(text, viewModel)
            }
        }

        viewModel = EditViewModel(notesRepo, labelsRepo, prefs,
            DefaultReminderAlarmManager(notesRepo, prefs, MockAlarmCallback()),
            editableTextProvider, SavedStateHandle())
        viewModel.setMaxUndoActions(UndoManager.NO_MAX_ACTIONS)
    }

    private fun checkFocusChange() {
        val focusChangeEvent = (viewModel.focusEvent.value ?: return)
        if (focusChangeEvent.hasBeenHandled) {
            return
        }

        val focusChange = focusChangeEvent.requireUnhandledContent()
        val listItems = viewModel.editItems.getOrAwaitValue()
        // We don't care about whether item exists, in the test the item should always exist.
        // Focus should be in a text item and in a valid position in the text.
        assertTrue(focusChange.index in listItems.indices)
        val item = listItems[focusChange.index]
        assertTrue(item is EditTextItem)
        assertTrue(focusChange.textPos in 0..item.text.text.length)
    }

    private fun randomTest(moveCheckedToBottom: Boolean, shownDateField: ShownDateField) = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn moveCheckedToBottom
        whenever(prefs.shownDateField) doReturn shownDateField

        val rng = Random(0)

        val noteId = 1L
        lateinit var note: NoteWithLabels
        val checkItems = suspend {
            viewModel.saveNote()
            note = notesRepo.getNoteByIdWithLabels(noteId)!!
            checkIfListItemsAreCorrect(editItems, moveCheckedToBottom, shownDateField, note)
            checkFocusChange()
        }

        viewModel.start(noteId)
        checkItems()
        val originalNote = note

        data class Action(
            val weight: Int,
            val predicate: () -> Boolean = { true },
            val action: suspend () -> String,
        )

        val isListNote = { note.note.type == NoteType.LIST }
        val isNonEmptyListNote = { isListNote() && editItems.any { it is EditItemItem } }
        val isAnyCheckedListNote = { isListNote() && editItems.any { it is EditItemItem && it.checked } }
        val isAnyUncheckedListNote = { isListNote() && editItems.any { it is EditItemItem && !it.checked } }

        val getRandomItemIndex = { predicate: (EditListItem) -> Boolean ->
            editItems.withIndex().filter { predicate(it.value) }.random(rng).index
        }

        val actions = listOf(
            Action(20) {
                val item = editItems.filterIsInstance<EditTextItem>().random(rng)
                var text = randomString(0..100, rng, TEXT_CHARS)
                if (item is EditTitleItem) {
                    text = text.replace("\n", "")
                }
                val start = (0..item.text.text.length).random(rng)
                val end = (start..item.text.text.length).random(rng)
                item.text.replace(start, end, text)
                "replace($start, $end, \"${text.replace("\n", "\\n")}\")"
            },
            Action(5) {
                viewModel.toggleNoteType()
                "toggleNoteType()"
            },
            Action(2) {
                viewModel.onNoteTitleEnterPressed()
                "onNoteTitleEnterPressed()"
            },
            Action(10) {
                val index = getRandomItemIndex { it is EditTextItem }
                viewModel.onNoteItemBackspacePressed(index)
                "onNoteItemBackspacePressed($index)"
            },
            Action(10, isListNote) {
                val keepChecked = rng.nextBoolean()
                viewModel.convertToText(keepChecked)
                "convertToText($keepChecked)"
            },
            Action(10, isListNote) {
                viewModel.onNoteItemAddClicked()
                "onNoteItemAddClicked()"
            },
            Action(1, isNonEmptyListNote) {
                viewModel.sortItems()
                "sortItems()"
            },
            Action(20, isNonEmptyListNote) {
                val index = getRandomItemIndex { it is EditItemItem }
                val checked = !(editItems[index] as EditItemItem).checked
                viewModel.onNoteItemCheckChanged(index, checked)
                "onNoteItemCheckChanged($index, $checked)"
            },
            Action(10, isNonEmptyListNote) {
                val index = getRandomItemIndex { it is EditItemItem }
                viewModel.onNoteItemDeleteClicked(index)
                "onNoteItemDeleteClicked($index)"
            },
            Action(10, isAnyUncheckedListNote) {
                val from = getRandomItemIndex { it is EditItemItem && !it.checked }
                val to = getRandomItemIndex { it is EditItemItem && !it.checked }
                viewModel.onNoteItemSwapped(from, to)
                "onNoteItemSwapped($from, $to)"
            },
            Action(1, isAnyCheckedListNote) {
                viewModel.uncheckAllItems()
                "uncheckAllItems()"
            },
            Action(1, isAnyCheckedListNote) {
                viewModel.deleteCheckedItems()
                "deleteCheckedItems()"
            },
            Action(1) {
                viewModel.togglePin()
                "togglePin()"
            },
            Action(1) {
                val reminder = if (rng.nextBoolean()) null else ANY_REMINDER
                viewModel.onReminderChange(reminder)
                "onReminderChange($reminder)"
            },
            Action(1, { note.labels.isNotEmpty() }) {
                val labelId = note.labels.random(rng).id
                labelsRepo.deleteLabelRefs(listOf(LabelRef(noteId, labelId)))
                viewModel.start(noteId)
                "delete label ref $labelId"
            },
            Action(1, { note.labels.size < labelsRepo.labelsCount }) {
                val labelId = labelsRepo.getAllLabelsByUsage().first().last().id   // Least used label
                labelsRepo.addLabelRefs(listOf(LabelRef(noteId, labelId)))
                viewModel.start(noteId)
                "add label ref $labelId"
            },
        )

        repeat(3000) {
            val available = actions.filter { it.predicate() }
            val totalWeight = available.sumOf { it.weight }
            val choice = rng.nextInt(totalWeight)
            var total = 0.0
            for (action in available) {
                total += action.weight
                if (total >= choice) {
                    action.action()
                    break
                }
            }

            checkItems()
            advanceTimeBy(EditViewModel.UNDO_TEXT_DEBOUNCE_DELAY * rng.nextDouble(0.0, 2.0))
        }

        val noteAfter = note

        while (viewModel.editActionsAvailability.value?.undo == EditActionAvailability.AVAILABLE) {
            viewModel.undo()
            checkItems()
        }
        assertNoteEqualsForUndo(originalNote.note, note.note)

        while (viewModel.editActionsAvailability.value?.redo == EditActionAvailability.AVAILABLE) {
            viewModel.redo()
            checkItems()
        }
        assertNoteEqualsForUndo(noteAfter.note, note.note)
    }

    private fun assertNoteEqualsForUndo(note1: Note, note2: Note) {
        assertEquals(note1.type, note2.type)
        assertEquals(note1.title, note2.title)
        assertEquals(note1.content, note2.content)
        assertEquals(note1.metadata, note2.metadata)
    }

    @Test
    fun `should undo and redo correctly, no date`() = runTest {
        randomTest(moveCheckedToBottom = false, shownDateField = ShownDateField.NONE)
    }

    @Test
    fun `should undo and redo correctly, added date`() = runTest {
        randomTest(moveCheckedToBottom = false, shownDateField = ShownDateField.ADDED)
    }

    @Test
    fun `should undo and redo correctly, no date, move checked to bottom`() = runTest {
        randomTest(moveCheckedToBottom = true, shownDateField = ShownDateField.NONE)
    }

    companion object {
        private val TEXT_CHARS = (0..<26).map { 'a' + it }.joinToString("") + "\n"
        private val ANY_REMINDER = Reminder(Date(1), null, Date(1), 1, false)
    }
}
