/*
 * Copyright 2020 Nicolas Maltais
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

package com.maltaisn.notes.ui.main

import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.StatusChange


sealed class MessageEvent {

    object BlankNoteDiscardEvent : MessageEvent()

    class StatusChangeEvent(val statusChange: StatusChange) : MessageEvent() {

        val messageId = when (statusChange.newStatus) {
            NoteStatus.ACTIVE -> if (statusChange.oldStatus == NoteStatus.TRASHED) {
                R.plurals.message_move_restore
            } else {
                R.plurals.message_move_unarchive
            }
            NoteStatus.ARCHIVED -> R.plurals.message_move_archive
            NoteStatus.TRASHED -> R.plurals.message_move_delete
        }
    }

}
