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
import com.maltaisn.notes.ui.edit.EditFocusChange
import com.maltaisn.notes.ui.edit.EditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditListItem

/**
 * Interface marking an edit event that can be undone and done/redone.
 */
sealed interface EditEvent

data class EventPayload(
    val editableTextProvider: EditableTextProvider,
    val listItems: MutableList<EditListItem>,
    val moveCheckedToBottom: Boolean = false,
)

/**
 * Interface for an edit event that operates on list items only.
 * The undo / redo callbacks take a list of items to be modified and can return a focus change event.
 */
sealed interface ItemEditEvent : EditEvent {
    /** Undo this event on a list of items, return an optional focus change. */
    fun undo(payload: EventPayload): EditFocusChange?

    /** Do/redo this event on a list of items, return an optional focus change. */
    fun redo(payload: EventPayload): EditFocusChange?

    /** Merge this event with another that comes afterwards. Returns `null` if not mergeable. */
    fun mergeWith(event: ItemEditEvent): ItemEditEvent? = null
}

/**
 * Interface for an edit event that changes the whole note.
 * The undo / redo methods return the new note to use based on the current one.
 * The note status cannot be changed with such an event.
 */
sealed interface NoteEditEvent : EditEvent {
    fun undo(note: Note): Note
    fun redo(note: Note): Note
}
