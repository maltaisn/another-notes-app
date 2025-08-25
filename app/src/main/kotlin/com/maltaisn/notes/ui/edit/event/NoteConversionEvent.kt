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

package com.maltaisn.notes.ui.edit.event

import com.maltaisn.notes.model.entity.Note

/**
 * Convert from [oldNote] to [newNote].
 * Only the type, content and metadata are changed by the event.
 */
data class NoteConversionEvent(
    val oldNote: Note,
    val newNote: Note
) : NoteEditEvent {

    private fun updateNote(note: Note, other: Note) = note.copy(
        type = other.type,
        content = other.content,
        metadata = other.metadata,
    )

    override fun undo(note: Note): Note {
        return updateNote(note, oldNote)
    }

    override fun redo(note: Note): Note {
        return updateNote(note, newNote)
    }
}
