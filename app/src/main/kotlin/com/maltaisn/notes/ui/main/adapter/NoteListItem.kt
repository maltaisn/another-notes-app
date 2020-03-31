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

package com.maltaisn.notes.ui.main.adapter

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteType


sealed class NoteListItem(open val id: Long) {
    abstract val type: Int
}

data class NoteItem(override val id: Long, val note: Note, val checked: Boolean) : NoteListItem(id) {
    override val type: Int
        get() = when (note.type) {
            NoteType.TEXT -> NoteAdapter.VIEW_TYPE_TEXT_NOTE
            NoteType.LIST -> NoteAdapter.VIEW_TYPE_LIST_NOTE
        }
}

class MessageItem(id: Long, val message: Int, vararg val args: Any) : NoteListItem(id) {
    override val type: Int
        get() = NoteAdapter.VIEW_TYPE_MESSAGE
}
