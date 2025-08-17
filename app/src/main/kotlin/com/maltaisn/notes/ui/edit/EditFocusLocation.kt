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

import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTextItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem

/**
 * Location of a focusable text item in the edit list.
 * The item index can be found from this.
 */
sealed interface EditFocusLocation {

    fun findItemIn(items: List<EditListItem>): EditTextItem =
        items[findIndexIn(items)] as EditTextItem

    fun findIndexIn(items: List<EditListItem>): Int

    object Title : EditFocusLocation {
        override fun findIndexIn(items: List<EditListItem>) =
            items.indexOfFirst { it is EditTitleItem }
    }

    object Content : EditFocusLocation {
        override fun findIndexIn(items: List<EditListItem>) =
            items.indexOfFirst { it is EditContentItem }
    }

    data class Item(val actualPos: Int) : EditFocusLocation {
        override fun findIndexIn(items: List<EditListItem>) =
            items.indexOfFirst { it is EditItemItem && it.actualPos == actualPos }
    }

    companion object {
        fun fromItem(item: EditTextItem) = when (item) {
            is EditTitleItem -> Title
            is EditContentItem -> Content
            is EditItemItem -> Item(item.actualPos)
        }
    }
}