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

package com.maltaisn.notes.ui.note.adapter

import androidx.recyclerview.widget.DiffUtil


class NoteListDiffCallback : DiffUtil.ItemCallback<NoteListItem>() {

    override fun areItemsTheSame(old: NoteListItem, new: NoteListItem) = old.id == new.id

    override fun areContentsTheSame(old: NoteListItem, new: NoteListItem): Boolean {
        if (new.type != old.type) {
            return false
        }

        return when (new) {
            is MessageItem -> {
                old as MessageItem
                new.message == old.message
            }
            is HeaderItem -> {
                old as HeaderItem
                new.title == old.title
            }
            is NoteItem -> {
                // Only checked state, title, content and metadata
                // have influence on visual representation of notes.
                old as NoteItem
                val oldNote = old.note
                val newNote = new.note
                new.checked == old.checked
                        && new.titleHighlights == old.titleHighlights
                        && new.contentHighlights == old.contentHighlights
                        && newNote.title == oldNote.title
                        && newNote.content == oldNote.content
                        && newNote.metadata == oldNote.metadata
                        && newNote.status == oldNote.status
            }
        }
    }

}
