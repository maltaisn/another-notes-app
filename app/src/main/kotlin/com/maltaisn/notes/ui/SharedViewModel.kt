/*
 * Copyright 2021 Nicolas Maltais
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
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.home.HomeDestination
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared view model used to send Snackbars from a fragment being popped from backstack.
 */
class SharedViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val reminderAlarmManager: ReminderAlarmManager,
) : ViewModel() {

    // No need to save this in saved state handle because Snackbar
    //
    private var lastStatusChange: StatusChange? = null

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _reminderChangeEvent = MutableLiveData<Event<Reminder?>>()
    val reminderChangeEvent: LiveData<Event<Reminder?>>
        get() = _reminderChangeEvent

    private val _changeDestinationEvent = MutableLiveData<Event<HomeDestination>>()
    val changeDestinationEvent: LiveData<Event<HomeDestination>>
        get() = _changeDestinationEvent


    fun onBlankNoteDiscarded() {
        // Not shown from EditFragment so that FAB is pushed up.
        _messageEvent.send(R.string.edit_message_blank_note_discarded)
    }

    fun onStatusChange(statusChange: StatusChange) {
        lastStatusChange = statusChange
        _statusChangeEvent.send(statusChange)
    }

    fun undoStatusChange() {
        val change = lastStatusChange ?: return
        viewModelScope.launch {
            notesRepository.updateNotes(change.oldNotes)
        }

        if (change.newStatus == NoteStatus.DELETED) {
            // Notes were deleted, removing any reminder alarm that had been set. Set them back.
            for (note in change.oldNotes) {
                if (note.reminder != null) {
                    reminderAlarmManager.setNoteReminderAlarm(note)
                }
            }
        }

        lastStatusChange = null
    }

    fun onReminderChange(reminder: Reminder?) {
        _reminderChangeEvent.send(reminder)
    }

    fun changeDestination(destination: HomeDestination) {
        _changeDestinationEvent.send(destination)
    }
}
