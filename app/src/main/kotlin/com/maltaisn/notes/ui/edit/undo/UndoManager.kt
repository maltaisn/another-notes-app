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
 * Class used to store and manage the undo queue.
 * A position to the current action in the queue is kept to indicate which actions go before (to undo)
 * and which actions go after (to redo) from the current state.
 *
 * This class is not thread-safe!
 */
class UndoManager {

    var maxActions = NO_MAX_ACTIONS

    var isInBatchMode = false
        private set

    private val queue = ArrayDeque<UndoAction>()
    private var position = POSITION_NONE
    private val batchActions = mutableListOf<ItemUndoAction>()

    val canUndo: Boolean
        get() = position != POSITION_NONE || batchActions.isNotEmpty()

    val canRedo: Boolean
        get() = position < queue.lastIndex && batchActions.isEmpty()

    /**
     * Clear all actions in the manager and stops the batch mode if started.
     */
    fun clear() {
        isInBatchMode = false
        queue.clear()
        position = POSITION_NONE
        batchActions.clear()
    }

    /**
     * Moves the undo head back and returns the action to undo, or `null` if there's nothing to undo.
     */
    fun undo(): UndoAction? {
        endBatch()
        return if (position == POSITION_NONE) {
            null
        } else {
            val action = queue[position]
            position--
            action
        }
    }

    /**
     * Moves the undo head forward and returns the action to redo, or `null` if there's nothing to redo.
     */
    fun redo(): UndoAction? {
        endBatch()
        return if (position == queue.lastIndex) {
            null
        } else {
            position++
            queue[position]
        }
    }

    @Synchronized
    fun append(action: UndoAction) {
        if (isInBatchMode) {
            check(action is ItemUndoAction) { "Cannot do non-item actions in batch mode." }
            if (action is BatchUndoAction) {
                batchActions += action.actions
            } else if (batchActions.isNotEmpty()) {
                val mergedAction = batchActions.last().mergeWith(action)
                if (mergedAction != null) {
                    batchActions[batchActions.size - 1] = mergedAction
                } else {
                    batchActions += action
                }
            } else {
                batchActions += action
            }
            return
        }

        if (position < queue.lastIndex) {
            // Not at the latest action in the undo queue, remove all actions after.
            queue.subList(position + 1, queue.size).clear()
        } else if (queue.size == maxActions) {
            queue.removeFirst()
        }
        queue.addLast(action)
        position = queue.lastIndex
    }

    /**
     * Start batch mode, in which all actions will count as a single one.
     * Only [ItemUndoAction] are supported in batch mode.
     */
    fun startBatch() {
        isInBatchMode = true
    }

    /**
     * End batch mode and add batch action to undo list.
     */
    fun endBatch() {
        if (isInBatchMode) {
            isInBatchMode = false
            when (batchActions.size) {
                0 -> {}
                1 -> append(batchActions.first())
                else -> append(BatchUndoAction(batchActions.toList()))
            }
            batchActions.clear()
        }
    }

    companion object {
        private const val POSITION_NONE = -1

        const val NO_MAX_ACTIONS = Int.MAX_VALUE
    }
}
