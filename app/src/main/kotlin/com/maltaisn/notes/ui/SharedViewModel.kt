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

package com.maltaisn.notes.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject


class SharedViewModel @Inject constructor(
        private val notesRepository: NotesRepository) : ViewModel() {

    private var lastStatusChange: StatusChange? = null

    private val _statusChangeMessageEvent = MutableLiveData<Event<StatusChangeMessage>>()
    val statusChangeMessageEvent: LiveData<Event<StatusChangeMessage>>
        get() = _statusChangeMessageEvent


    fun doStatusChange(statusChange: StatusChange) {
        lastStatusChange = statusChange

        // Show status change message
        _statusChangeMessageEvent.value = Event(StatusChangeMessage(when (statusChange.newStatus) {
            NoteStatus.ACTIVE -> if (statusChange.oldStatus == NoteStatus.TRASHED) {
                R.plurals.message_move_restore
            } else {
                R.plurals.message_move_unarchive
            }
            NoteStatus.ARCHIVED -> R.plurals.message_move_archive
            NoteStatus.TRASHED -> R.plurals.message_move_delete
        }, statusChange.notes.size))
    }

    fun undoStatusChange() {
        val change = lastStatusChange ?: return
        viewModelScope.launch {
            val date = Date()
            for ((i, note) in change.notes.withIndex()) {
                notesRepository.updateNote(note.copy(
                        status = change.oldStatus,
                        lastModifiedDate = change.oldModifiedDates[i]))
            }
        }
        lastStatusChange = null
    }

    data class StatusChangeMessage(val messageId: Int, val count: Int)

}
