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

import com.maltaisn.notes.ui.edit.EditFocusChange

/**
 * A sequence of undo actions that can be undone and redone as one.
 * Only supports batching for [ItemUndoAction].
 */
data class BatchUndoAction(val actions: List<ItemUndoAction>) : ItemUndoAction {

    override fun mergeWith(action: ItemUndoAction): BatchUndoAction {
        return if (action is BatchUndoAction) {
            BatchUndoAction(actions + action.actions)
        } else {
            BatchUndoAction(actions + action)
        }
    }

    override fun undo(payload: UndoPayload): EditFocusChange? {
        // Undo all actions in reverse order, returns first focus change
        var focusChange: EditFocusChange? = null
        for (action in actions.asReversed()) {
            focusChange = action.undo(payload)
        }
        return focusChange
    }

    override fun redo(payload: UndoPayload): EditFocusChange? {
        // Redo all actions in order, returns last focus change
        var focusChange: EditFocusChange? = null
        for (action in actions) {
            focusChange = action.redo(payload)
        }
        return focusChange
    }
}