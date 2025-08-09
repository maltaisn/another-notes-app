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

package com.maltaisn.notes.receiver

import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteType
import javax.inject.Inject

class NotificationContentProvider @Inject constructor(
    private val prefs: PrefsManager,
) {

    fun getContent(note: Note, maxListItems: Int = Int.MAX_VALUE) = when (note.type) {
        NoteType.TEXT -> note.content
        NoteType.LIST -> {
            val items = note.listItems
            if (prefs.moveCheckedToBottom) {
                items.sortBy { it.checked }
            }
            if (items.size > maxListItems) {
                items.subList(maxListItems, items.size).clear()
            }
            buildString {
                for (item in items) {
                    append(if (item.checked) '☑' else '☐')
                    append(' ')
                    append(item.content)
                    append('\n')
                }
                deleteAt(lastIndex)
            }
        }
    }

}