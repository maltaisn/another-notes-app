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

package com.maltaisn.notes.ui.edit.adapter

sealed class EditListItem {
    abstract val type: Int
}

data class EditTitleItem(
    var title: EditableText,
    var editable: Boolean
) : EditListItem() {
    override val type: Int
        get() = EditAdapter.VIEW_TYPE_TITLE
}

data class EditContentItem(
    var content: EditableText,
    val editable: Boolean
) : EditListItem() {
    override val type: Int
        get() = EditAdapter.VIEW_TYPE_CONTENT
}

data class EditItemItem(
    var content: EditableText,
    var checked: Boolean,
    val editable: Boolean
) : EditListItem() {
    override val type: Int
        get() = EditAdapter.VIEW_TYPE_ITEM
}

object EditItemAddItem : EditListItem() {
    override val type: Int
        get() = EditAdapter.VIEW_TYPE_ITEM_ADD
}

/**
 * This is needed so the view model can know the text and each item at all times and be able
 * to change it. An interface is used to provide a different test implementation.
 * The alternative would be the call the view model every time an item text is changed by
 * user, which wouldn't be great for performance since `toString()` would be needed every time.
 */
interface EditableText {
    val text: CharSequence

    fun append(text: CharSequence)
    fun replaceAll(text: CharSequence)
}
