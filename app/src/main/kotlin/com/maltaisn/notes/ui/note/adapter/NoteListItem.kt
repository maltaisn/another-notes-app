/*
 * Copyright 2021 Nicolas Maltais
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

package com.maltaisn.notes.ui.note.adapter

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.ui.note.Highlighted
import com.maltaisn.notes.ui.note.adapter.NoteAdapter.ViewType

sealed interface NoteListItem {
    val id: Long
    val type: ViewType
}

sealed interface NoteItem : NoteListItem {
    val note: Note
    val labels: List<Label>
    var checked: Boolean
    val title: Highlighted
    val showMarkAsDone: Boolean

    fun withChecked(checked: Boolean): NoteItem
}

data class NoteItemText(
    override val id: Long,
    override val note: Note,
    override val labels: List<Label>,
    override var checked: Boolean,
    override val title: Highlighted,
    val content: Highlighted,
    override val showMarkAsDone: Boolean,
) : NoteItem {

    override val type: ViewType
        get() = ViewType.TEXT_NOTE

    override fun withChecked(checked: Boolean) = copy(checked = checked)
}

data class NoteItemList(
    override val id: Long,
    override val note: Note,
    override val labels: List<Label>,
    override var checked: Boolean,
    override val title: Highlighted,
    val items: List<Highlighted>,
    val itemsChecked: List<Boolean>,
    val overflowCount: Int,
    val onlyCheckedInOverflow: Boolean,
    override val showMarkAsDone: Boolean,
) : NoteItem {

    override val type: ViewType
        get() = ViewType.LIST_NOTE

    override fun withChecked(checked: Boolean) = copy(checked = checked)
}

data class HeaderItem(
    override val id: Long,
    @StringRes val title: Int
) : NoteListItem {

    override val type get() = ViewType.HEADER
}

data class MessageItem(
    override val id: Long,
    @StringRes @PluralsRes val message: Int,
    val args: List<Any> = emptyList(),
) : NoteListItem {

    override val type get() = ViewType.MESSAGE
}
