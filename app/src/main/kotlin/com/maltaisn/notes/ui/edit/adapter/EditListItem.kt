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

package com.maltaisn.notes.ui.edit.adapter

import com.maltaisn.notes.ui.edit.EditableText
import com.maltaisn.notes.ui.edit.adapter.EditAdapter.ViewType

sealed class EditListItem {
    abstract val type: ViewType
}

interface EditTextItem {
    var text: EditableText
    val editable: Boolean
}

data class EditDateItem(
    val date: Long
) : EditListItem() {

    override val type get() = ViewType.DATE
}

data class EditTitleItem(
    override var text: EditableText,
    override val editable: Boolean,
) : EditListItem(), EditTextItem {

    override val type get() = ViewType.TITLE
}

data class EditContentItem(
    override var text: EditableText,
    override val editable: Boolean,
) : EditListItem(), EditTextItem {

    override val type get() = ViewType.CONTENT
}

data class EditItemItem(
    override var text: EditableText,
    var checked: Boolean,
    override val editable: Boolean,
    var actualPos: Int,
) : EditListItem(), EditTextItem {

    // This flag is used to indicate that the item's content has changed, to trigger a rebinding.
    // It's used specifically for check: user can toggle the checkbox, which doesn't require an update because
    // the checkbox already has the correct state. Not only that, but if the list is updated, an annoying animation
    // is shown even though nothing changed. If, however, the view model unchecks the item, it must be updated.
    private var dirty: Boolean = false

    fun shouldUpdate(): Boolean {
        return if (dirty) {
            dirty = false
            true
        } else {
            false
        }
    }

    fun requestUpdate() {
        dirty = true
    }

    override val type get() = ViewType.ITEM
}

object EditItemAddItem : EditListItem() {

    override val type get() = ViewType.ITEM_ADD
}

data class EditCheckedHeaderItem(
    var count: Int
) : EditListItem() {

    override val type get() = ViewType.ITEM_CHECKED_HEADER
}

data class EditChipsItem(
    // Chips can be Label or Reminder
    val chips: List<Any>
) : EditListItem() {

    override val type get() = ViewType.ITEM_CHIPS
}

