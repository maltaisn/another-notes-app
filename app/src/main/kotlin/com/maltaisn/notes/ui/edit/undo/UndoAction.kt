/*
 * Copyright 2022 Nicolas Maltais
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
import com.maltaisn.notes.ui.edit.EditFocusChange
import com.maltaisn.notes.ui.edit.EditViewModel
import com.maltaisn.notes.ui.edit.adapter.EditListItem

/**
 * Interface marking an action that can be undone and redone.
 */
sealed interface UndoAction

/**
 * Interface for an undo action that operates on list items only.
 * The undo / redo callbacks take a list of items to be modified and can return a focus change event.
 */
sealed interface ItemUndoAction : UndoAction {
    /** Undo this action on a list of items, return an optional focus change. */
    fun undo(listItems: MutableList<EditListItem>): EditFocusChange?

    /** Redo this action on a list of items, return an optional focus change. */
    fun redo(listItems: MutableList<EditListItem>): EditFocusChange?

    /** Merge this action with another that comes afterwards. Returns `null` if not mergeable. */
    fun mergeWith(action: ItemUndoAction): ItemUndoAction? = null
}

/**
 * Interface for an undo action that changes the whole note.
 * The undo / redo callback return the new note to use.
 * A focus change is always made to the first focusable item in note.
 *
 * Note: for now this only supports changing the note type. If further support is needed, the `undoRedoAfterUpdate`
 * method will need to be changed in [EditViewModel] to update the live data for other attributes.
 */
sealed interface NoteUndoAction : UndoAction {
    /** Undo this action on a list of items, return the old note. */
    fun undo(): Note
    fun redo(): Note
}
