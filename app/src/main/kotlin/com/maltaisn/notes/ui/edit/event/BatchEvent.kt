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

import com.maltaisn.notes.ui.edit.EditFocusChange

/**
 * A sequence of edit events that can be undone and redone as one.
 * Only supports batching for [ItemEditEvent].
 */
data class BatchEvent(val events: List<ItemEditEvent>) : ItemEditEvent {

    override fun mergeWith(event: ItemEditEvent): BatchEvent {
        return if (event is BatchEvent) {
            BatchEvent(events + event.events)
        } else {
            BatchEvent(events + event)
        }
    }

    override fun undo(payload: EventPayload): EditFocusChange? {
        // Undo all events in reverse order, returns first focus change
        var focusChange: EditFocusChange? = null
        for (event in events.asReversed()) {
            focusChange = event.undo(payload)
        }
        return focusChange
    }

    override fun redo(payload: EventPayload): EditFocusChange? {
        // Redo all events in order, returns last focus change
        var focusChange: EditFocusChange? = null
        for (event in events) {
            focusChange = event.redo(payload)
        }
        return focusChange
    }
}