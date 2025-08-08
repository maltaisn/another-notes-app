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
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTextItem

data class UndoFocusChange(
    val location: UndoActionLocation,
    val pos: Int,
    val itemExists: Boolean = false,
) {

    fun toEditFocusChange(listItems: List<EditListItem>) =
        EditFocusChange(location.findIndexIn(listItems), pos, itemExists)

    companion object {
        fun atPosOfItem(item: EditTextItem, pos: Int, itemExists: Boolean = false) =
            UndoFocusChange(UndoActionLocation.fromItem(item), pos, itemExists)

        fun atStartOfItem(item: EditTextItem, itemExists: Boolean = false) =
            atPosOfItem(item, 0, itemExists)

        fun atEndOfItem(item: EditTextItem, itemExists: Boolean = false) =
            atPosOfItem(item, item.text.text.length, itemExists)
    }
}
