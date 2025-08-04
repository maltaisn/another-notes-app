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
import com.maltaisn.notes.ui.edit.EditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditListItem

/**
 * Remove existing list items.
 */
data class ItemRemoveUndoAction(
    override val itemPos: Int,
    override val text: List<String>,
    override val checked: Boolean,
    override val actualPos: Int,
    val focusAfter: EditFocusChange? = null,
) : ItemChangeUndoAction {

    override fun undo(
        editableTextProvider: EditableTextProvider,
        listItems: MutableList<EditListItem>
    ): EditFocusChange? {
        addItems(editableTextProvider, listItems)
        return EditFocusChange(itemPos + text.lastIndex, text.last().length, false)
    }

    override fun redo(
        editableTextProvider: EditableTextProvider,
        listItems: MutableList<EditListItem>
    ): EditFocusChange? {
        removeItems(listItems)
        return focusAfter
    }
}
