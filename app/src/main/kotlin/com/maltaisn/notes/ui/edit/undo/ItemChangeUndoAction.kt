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

import com.maltaisn.notes.ui.edit.EditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem

/**
 * Interface for undo action that operates on items starting at [itemPos] in the list,
 * with [text] being the items' text.
 */
sealed interface ItemChangeUndoAction : ItemUndoAction {

    val itemPos: Int
    val text: List<String>
    val checked: Boolean
    val actualPos: Int

    val itemCount: Int
        get() = text.size

    fun removeItems(listItems: MutableList<EditListItem>) {
        listItems.subList(itemPos, itemPos + itemCount).clear()

        // Shift all actual pos for items below
        for (listItem in listItems) {
            if (listItem is EditItemItem && listItem.actualPos >= actualPos) {
                listItem.actualPos -= itemCount
            }
        }
    }

    fun addItems(
        editableTextProvider: EditableTextProvider,
        listItems: MutableList<EditListItem>
    ) {
        // Shift all actual pos for items below
        for (listItem in listItems) {
            if (listItem is EditItemItem && listItem.actualPos >= actualPos) {
                listItem.actualPos += itemCount
            }
        }

        // Add items
        for ((i, itemText) in text.withIndex()) {
            listItems.add(itemPos + i, EditItemItem(editableTextProvider.create(itemText),
                checked = checked, editable = true, actualPos + i))
        }
    }
}
