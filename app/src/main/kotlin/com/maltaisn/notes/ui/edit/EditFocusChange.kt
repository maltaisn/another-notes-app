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

import com.maltaisn.notes.ui.edit.adapter.EditTextItem

/**
 * Represents a change of focus to a text item in the edit list.
 *
 * @param itemExists This should be `true` if the list currently seen by the adapter contains
 * the item targeted by this focus change. Otherwise, the adapter will wait until the new item
 * is bound to a view holder before changing the focus.
 */
data class EditFocusChange(
    val location: EditFocusLocation,
    val textPos: Int,
    val itemExists: Boolean = false,
) {

    companion object {
        fun atPosOfItem(item: EditTextItem, textPos: Int, itemExists: Boolean = false) =
            EditFocusChange(EditFocusLocation.fromItem(item), textPos, itemExists)

        fun atStartOfItem(item: EditTextItem, itemExists: Boolean = false) =
            atPosOfItem(item, 0, itemExists)

        fun atEndOfItem(item: EditTextItem, itemExists: Boolean = false) =
            atPosOfItem(item, item.text.text.length, itemExists)
    }
}