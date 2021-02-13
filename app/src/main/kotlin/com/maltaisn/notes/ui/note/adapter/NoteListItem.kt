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
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.note.adapter.NoteAdapter.ViewType

sealed class NoteListItem {
    abstract val id: Long
    abstract val type: ViewType
}

data class NoteItem(
    override val id: Long,
    val note: Note,
    val checked: Boolean = false,
    val titleHighlights: List<IntRange> = emptyList(),
    val contentHighlights: List<IntRange> = emptyList(),
    val showMarkAsDone: Boolean = false,
) : NoteListItem() {

    override val type: ViewType
        get() = when (note.type) {
            NoteType.TEXT -> ViewType.TEXT_NOTE
            NoteType.LIST -> ViewType.LIST_NOTE
        }
}

data class HeaderItem(
    override val id: Long,
    @StringRes val title: Int
) : NoteListItem() {

    override val type get() = ViewType.HEADER
}

data class MessageItem(
    override val id: Long,
    @StringRes @PluralsRes val message: Int,
    val args: List<Any>
) : NoteListItem() {

    override val type get() = ViewType.MESSAGE
}
