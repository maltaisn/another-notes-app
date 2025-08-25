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
import com.maltaisn.notes.ui.edit.EditFocusLocation

/**
 * Text in range [start] to [end] (exclusive) changed from [oldText] to [newText],
 * in an item at [location].
 *
 * The need for merging [TextChangeEvent] arises from the fact that the text watcher on EditText
 * is called for every character changed, which would create lots of objects each were stored.
 */
@ConsistentCopyVisibility
data class TextChangeEvent private constructor(
    val location: EditFocusLocation,
    val start: Int,
    val end: Int,
    val oldText: String,
    val newText: String
) : ItemEditEvent {

    override fun mergeWith(event: ItemEditEvent): TextChangeEvent? {
        return if (event is TextChangeEvent && location == event.location) {
            // There are five possible cases when merging events, four of which can be merged,
            // depending on the location of the new event relative to the old event.
            // This is a nightmare, surely it could be done more simply?
            val mergeStart: Int
            val mergeEnd: Int
            val mergeOldText: String
            val mergeNewText: String
            val startDiff = start - event.start
            when {
                event.start <= start && event.end >= start + newText.length -> {
                    // Old event is fully within new event (case: outside).
                    mergeStart = event.start
                    mergeEnd = event.end + (oldText.length - newText.length)
                    mergeOldText = event.oldText.substring(0, startDiff) +
                            oldText + event.oldText.substring(startDiff + newText.length)
                    mergeNewText = event.newText
                }
                event.start >= start && event.end <= start + newText.length -> {
                    // New event is fully within old event (case: inside).
                    mergeStart = start
                    mergeEnd = end
                    mergeOldText = oldText
                    mergeNewText = newText.substring(0, -startDiff) +
                            event.newText + newText.substring(event.oldText.length - startDiff)
                }
                event.start <= start && event.end >= start -> {
                    // New event replaces start of old event (case: before).
                    mergeStart = event.start
                    mergeEnd = end
                    mergeOldText = event.oldText.substring(0, startDiff) + oldText
                    mergeNewText = event.newText + newText.substring(event.end - start)
                }
                event.start >= start && event.start <= start + newText.length -> {
                    // New event replaces end of old event (case: after).
                    mergeStart = start
                    mergeEnd = event.end + (oldText.length - newText.length)
                    mergeOldText = oldText + event.oldText.substring(newText.length + startDiff)
                    mergeNewText = newText.substring(0, -startDiff) + event.newText
                }
                else -> {
                    // There's no overlap between the two events.
                    return null
                }
            }
            create(location, mergeStart, mergeEnd, mergeOldText, mergeNewText)
        } else {
            null
        }
    }

    override fun undo(payload: EventPayload): EditFocusChange {
        location.findItemIn(payload.listItems).text.replace(start, start + newText.length, oldText)
        return EditFocusChange(location, end, true)
    }

    override fun redo(payload: EventPayload): EditFocusChange {
        location.findItemIn(payload.listItems).text.replace(start, end, newText)
        return EditFocusChange(location, start + newText.length, true)
    }

    companion object {
        /**
         * Create text undo event, removing any common sequence at the beginning and end of [oldText] and [newText].
         * This is needed for merge to work correctly later on.
         */
        fun create(
            location: EditFocusLocation,
            start: Int,
            end: Int,
            oldText: String,
            newText: String
        ): TextChangeEvent {
            var s = 0
            while (s < oldText.length && s < newText.length && oldText[s] == newText[s]) {
                s++
            }
            var e = 0
            while (e < oldText.length - s && e < newText.length - s &&
                oldText[oldText.lastIndex - e] == newText[newText.lastIndex - e]
            ) {
                e++
            }
            return TextChangeEvent(location, start + s, end - e,
                oldText.substring(s, oldText.length - e), newText.substring(s, newText.length - e))
        }
    }
}
