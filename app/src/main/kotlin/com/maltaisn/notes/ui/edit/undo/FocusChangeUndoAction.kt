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

/**
 * Change focus from [before] position to [after] position.
 * If the undo action is combined with other in a [BatchUndoAction],
 * it makes sense for one of them to be `null`.
 * The item may or may not exist in the view at the time the focus change is requested.
 */
data class FocusChangeUndoAction(
    val before: UndoFocusChange? = null,
    val after: UndoFocusChange? = null,
) : ItemUndoAction {

    override fun mergeWith(action: ItemUndoAction): FocusChangeUndoAction? {
        return if (action is FocusChangeUndoAction) {
            FocusChangeUndoAction(action.before, after)
        } else {
            null
        }
    }

    override fun undo(payload: UndoPayload): UndoFocusChange? {
        return before
    }

    override fun redo(payload: UndoPayload): UndoFocusChange? {
        return after
    }
}
