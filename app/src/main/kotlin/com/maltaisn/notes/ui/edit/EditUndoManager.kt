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

package com.maltaisn.notes.ui.edit

import com.maltaisn.notes.ui.edit.event.BatchEvent
import com.maltaisn.notes.ui.edit.event.EditEvent
import com.maltaisn.notes.ui.edit.event.ItemEditEvent

/**
 * Class used to store and manage the undo queue.
 * A position to the current event in the queue is kept to indicate which events go before (to undo)
 * and which events go after (to redo) from the current state.
 *
 * This class is not thread-safe!
 */
class EditUndoManager {

    var maxEvents = NO_MAX_EVENTS

    var isInBatchMode = false
        private set

    private val queue = ArrayDeque<EditEvent>()
    private var position = POSITION_NONE
    private val batchEvents = mutableListOf<ItemEditEvent>()

    val canUndo: Boolean
        get() = position != POSITION_NONE || batchEvents.isNotEmpty()

    val canRedo: Boolean
        get() = position < queue.lastIndex && batchEvents.isEmpty()

    /**
     * Clear all events in the manager and stops the batch mode if started.
     */
    fun clear() {
        isInBatchMode = false
        queue.clear()
        position = POSITION_NONE
        batchEvents.clear()
    }

    /**
     * Moves the undo head back and returns the event to undo, or `null` if there's nothing to undo.
     */
    fun undo(): EditEvent? {
        endBatch()
        return if (position == POSITION_NONE) {
            null
        } else {
            val event = queue[position]
            position--
            event
        }
    }

    /**
     * Moves the undo head forward and returns the event to redo, or `null` if there's nothing to redo.
     */
    fun redo(): EditEvent? {
        endBatch()
        return if (position == queue.lastIndex) {
            null
        } else {
            position++
            queue[position]
        }
    }

    @Synchronized
    fun append(event: EditEvent) {
        if (isInBatchMode) {
            check(event is ItemEditEvent) { "Cannot do non-item events in batch mode." }
            if (event is BatchEvent) {
                batchEvents += event.events
            } else if (batchEvents.isNotEmpty()) {
                val mergedEvent = batchEvents.last().mergeWith(event)
                if (mergedEvent != null) {
                    batchEvents[batchEvents.size - 1] = mergedEvent
                } else {
                    batchEvents += event
                }
            } else {
                batchEvents += event
            }
            return
        }

        if (position < queue.lastIndex) {
            // Not at the latest event in the undo queue, remove all events after.
            queue.subList(position + 1, queue.size).clear()
        } else if (queue.size == maxEvents) {
            queue.removeFirst()
        }
        queue.addLast(event)
        position = queue.lastIndex
    }

    /**
     * Start batch mode, in which all events will count as a single one.
     * Only [ItemEditEvent] are supported in batch mode.
     */
    fun startBatch() {
        isInBatchMode = true
    }

    /**
     * End batch mode and add batch event to undo list.
     */
    fun endBatch() {
        if (isInBatchMode) {
            isInBatchMode = false
            when (batchEvents.size) {
                0 -> {}
                1 -> append(batchEvents.first())
                else -> append(BatchEvent(batchEvents.toList()))
            }
            batchEvents.clear()
        }
    }

    companion object {
        private const val POSITION_NONE = -1

        const val NO_MAX_EVENTS = Int.MAX_VALUE
    }
}