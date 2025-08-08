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

package com.maltaisn.notes.ui.edit.undo

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.ui.edit.EditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditListItem

/**
 * Interface marking an action that can be undone and redone.
 */
sealed interface UndoAction

data class UndoPayload(
    val editableTextProvider: EditableTextProvider,
    val listItems: MutableList<EditListItem>,
    val moveCheckedToBottom: Boolean = false,
)

/**
 * Interface for an undo action that operates on list items only.
 * The undo / redo callbacks take a list of items to be modified and can return a focus change event.
 */
sealed interface ItemUndoAction : UndoAction {
    /** Undo this action on a list of items, return an optional focus change. */
    fun undo(payload: UndoPayload): UndoFocusChange?

    /** Redo this action on a list of items, return an optional focus change. */
    fun redo(payload: UndoPayload): UndoFocusChange?

    /** Merge this action with another that comes afterwards. Returns `null` if not mergeable. */
    fun mergeWith(action: ItemUndoAction): ItemUndoAction? = null
}

/**
 * Interface for an undo action that changes the whole note.
 * The undo / redo callback return the new note to use based on the current one.
 * Following this action the list items are completely recreated and
 * a focus change is made to the first focusable item in the note.
 *
 * The note status cannot be changed with this action.
 */
sealed interface NoteUndoAction : UndoAction {
    fun undo(note: Note): Note
    fun redo(note: Note): Note
}
