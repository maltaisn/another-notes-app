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

import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.NoteWithLabels
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.edit.adapter.EditAdapter.ViewType
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditChipsItem
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.note.ShownDateField

fun checkIfListItemsAreCorrect(
    listItems: List<EditListItem>,
    moveCheckedToBottom: Boolean = false,
    shownDateField: ShownDateField = ShownDateField.NONE,
    note: NoteWithLabels? = null,
) {
    val foundTypes = mutableSetOf<ViewType>()

    var checkedCount = 0
    var lastActualPos = -1
    var checkedHeader: EditCheckedHeaderItem? = null
    val allActualPos = mutableListOf<Int>()

    val assertNoteType = { type: NoteType ->
        if (note != null) {
            assert(type == note.note.type) { "Unexpected item for note type" }
        }
    }

    for ((i, item) in listItems.withIndex()) {
        when (item) {
            is EditDateItem -> {
                assert(i == 0) { "Date item not at correct index" }
                if (note != null) {
                    val date = when (shownDateField) {
                        ShownDateField.ADDED -> note.note.addedDate
                        ShownDateField.MODIFIED -> note.note.lastModifiedDate
                        ShownDateField.NONE -> error("No date item expected")
                    }
                    assert(item.date == date.time) { "Wrong date value" }
                }
            }
            is EditContentItem -> {
                assertNoteType(NoteType.TEXT)
                val expectedIndex = if (shownDateField == ShownDateField.NONE) 1 else 2
                assert(i == expectedIndex) { "Content item not at correct index" }
            }
            is EditTitleItem -> {
                val expectedIndex = if (shownDateField == ShownDateField.NONE) 0 else 1
                assert(i == expectedIndex) { "Title item not at expected index " }
            }
            is EditItemItem -> {
                assertNoteType(NoteType.LIST)
                assert(item.actualPos > lastActualPos) { "Items should be in order of actual pos" }
                if (!moveCheckedToBottom) {
                    assert(ViewType.ITEM_ADD !in foundTypes) { "Add item should be after all list items" }
                }
                lastActualPos = item.actualPos
                if (item.checked) {
                    if (moveCheckedToBottom) {
                        assert(ViewType.ITEM_ADD in foundTypes) {
                            "Add item should be before checked list items"
                        }
                        assert(ViewType.CHECKED_HEADER in foundTypes) { "Checked header item should be before checked list items" }
                    }
                    checkedCount++
                }
                allActualPos += item.actualPos
            }
            is EditItemAddItem -> {
                assertNoteType(NoteType.LIST)
            }
            is EditCheckedHeaderItem -> {
                assertNoteType(NoteType.LIST)
                assert(moveCheckedToBottom) { "Unexpected checked header item" }
                checkedHeader = item
                lastActualPos = -1
            }
            is EditChipsItem -> {
                if (note != null) {
                    val labels = item.chips.filterIsInstance<Label>().toSet()
                    assert(labels == note.labels.toSet()) { "Wrong label chips" }
                    assert(item.chips.any { it is Reminder } == (note.note.reminder != null)) { "Expected reminder chip" }
                }
            }
        }
        foundTypes.add(item.type)
    }

    if (checkedCount > 0 && moveCheckedToBottom) {
        assert(checkedHeader!!.count == checkedCount) { "Checked header count is wrong " }
    }

    assert(ViewType.TITLE in foundTypes) { "Missing title item" }
    if (note != null) {
        if (note.note.type == NoteType.TEXT) {
            assert(ViewType.CONTENT in foundTypes) { "Missing content item" }
        }
        if (note.note.reminder != null || note.labels.isNotEmpty()) {
            assert(ViewType.CHIPS in foundTypes) { "Missing chips item" }
        }
    }
    if (shownDateField != ShownDateField.NONE) {
        assert(ViewType.DATE in foundTypes) { "Missing date item" }
    }
}
