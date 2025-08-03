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
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTextItem

/**
 * Text in range [start] to [end] (exclusive) changed from [oldText] to [newText].
 *
 * The need for merging [TextUndoAction] arises from the fact that the text watcher on EditText
 * is called for every character changed, which would create lots of objects each were stored.
 */
@ConsistentCopyVisibility
data class TextUndoAction private constructor(
    val itemPos: Int,
    val start: Int,
    val end: Int,
    val oldText: String,
    val newText: String
) : ItemUndoAction {

    override fun mergeWith(action: ItemUndoAction): TextUndoAction? {
        return if (action is TextUndoAction && itemPos == action.itemPos) {
            // There are five possible cases when merging actions, four of which can be merged,
            // depending on the location of the new action relative to the old action.
            // This is a nightmare, surely it could be done more simply?
            val mergeStart: Int
            val mergeEnd: Int
            val mergeOldText: String
            val mergeNewText: String
            val startDiff = start - action.start
            when {
                action.start <= start && action.end >= start + newText.length -> {
                    // Old action is fully within new action (case: outside).
                    mergeStart = action.start
                    mergeEnd = action.end + (oldText.length - newText.length)
                    mergeOldText = action.oldText.substring(0, startDiff) +
                            oldText + action.oldText.substring(startDiff + newText.length)
                    mergeNewText = action.newText
                }
                action.start >= start && action.end <= start + newText.length -> {
                    // New action is fully within old action (case: inside).
                    mergeStart = start
                    mergeEnd = end
                    mergeOldText = oldText
                    mergeNewText = newText.substring(0, -startDiff) +
                            action.newText + newText.substring(action.oldText.length - startDiff)
                }
                action.start <= start && action.end >= start -> {
                    // New action replaces start of old action (case: before).
                    mergeStart = action.start
                    mergeEnd = end
                    mergeOldText = action.oldText.substring(0, startDiff) + oldText
                    mergeNewText = action.newText + newText.substring(action.end - start)
                }
                action.start >= start && action.start <= start + newText.length -> {
                    // New action replaces end of old action (case: after).
                    mergeStart = start
                    mergeEnd = action.end + (oldText.length - newText.length)
                    mergeOldText = oldText + action.oldText.substring(newText.length + startDiff)
                    mergeNewText = newText.substring(0, -startDiff) + action.newText
                }
                else -> {
                    // There's no overlap between the two actions.
                    return null
                }
            }
            create(itemPos, mergeStart, mergeEnd, mergeOldText, mergeNewText)
        } else {
            null
        }
    }

    override fun undo(listItems: MutableList<EditListItem>): EditFocusChange {
        (listItems[itemPos] as EditTextItem).text.replace(start, start + newText.length, oldText)
        return EditFocusChange(itemPos, end, true)
    }

    override fun redo(listItems: MutableList<EditListItem>): EditFocusChange {
        (listItems[itemPos] as EditTextItem).text.replace(start, end, newText)
        return EditFocusChange(itemPos, start + newText.length, true)
    }

    companion object {
        /**
         * Create text undo action, removing any common sequence at the beginning and end of [oldText] and [newText].
         * This is needed for merge to work correctly later on.
         */
        fun create(
            itemPos: Int,
            start: Int,
            end: Int,
            oldText: String,
            newText: String
        ): TextUndoAction {
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
            return TextUndoAction(itemPos, start + s, end - e,
                oldText.substring(s, oldText.length - e), newText.substring(s, newText.length - e))
        }
    }
}
