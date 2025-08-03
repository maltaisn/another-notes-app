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

package com.maltaisn.notes.ui.edit.undo

import com.maltaisn.notes.ui.edit.EditFocusChange
import com.maltaisn.notes.ui.edit.EditViewModel
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem

/**
 * Insert [itemCount] items at [itemPos] in the list, with [text] as the initial text.
 * Items text are separated by newlines.
 */
data class ItemAddUndoAction(
    val itemPos: Int,
    val itemCount: Int,
    val text: String,
    val checked: Boolean,
    val actualPos: Int,
    val focusBefore: EditFocusChange? = null,
) : ItemUndoAction {

    override fun undo(listItems: MutableList<EditListItem>): EditFocusChange? {
        listItems.subList(itemPos, itemPos + itemCount).clear()

        // Shift all actual pos for items below
        for (listItem in listItems) {
            if (listItem is EditItemItem && listItem.actualPos >= actualPos) {
                listItem.actualPos -= itemCount
            }
        }

        return focusBefore
    }

    override fun redo(listItems: MutableList<EditListItem>): EditFocusChange {
        // Shift all actual pos for items below
        for (listItem in listItems) {
            if (listItem is EditItemItem && listItem.actualPos >= actualPos) {
                listItem.actualPos += itemCount
            }
        }

        // Add items
        var lastItemLen = 0
        for ((i, itemText) in text.splitToSequence('\n').withIndex()) {
            listItems.add(itemPos + i, EditItemItem(EditViewModel.DefaultEditableText(itemText),
                checked = checked, editable = true, actualPos + i))
            lastItemLen = itemText.length
        }

        return EditFocusChange(itemPos + itemCount - 1, lastItemLen, false)
    }
}
