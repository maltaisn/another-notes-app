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

package com.maltaisn.notes.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration

class MainViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val prefsManager: PrefsManager,
    private val jsonManager: JsonManager,
    private val reminderAlarmManager: ReminderAlarmManager
) : ViewModel() {

    private val _editNoteEvent = MutableLiveData<Event<Long>>()
    val editItemEvent: LiveData<Event<Long>>
        get() = _editNoteEvent

    private val _autoExportEvent = MutableLiveData<Event<String>>()
    val autoExportEvent: LiveData<Event<String>>
        get() = _autoExportEvent

    init {
        viewModelScope.launch {
            if (prefsManager.shouldAutoExport && prefsManager.autoExportUri == PrefsManager.AUTO_EXPORT_NO_URI) {
                // Auto export was enabled, but setup was not completed, disable it.
                prefsManager.disableAutoExport()
            }

            // Update all alarms for recurring reminders in case the previous alarm wasn't triggered.
            // This shouldn't technically happen, but there have been cases where recurring reminders failed.
            reminderAlarmManager.updateAllAlarms()

            // Periodically remove old notes in trash, and auto export if needed.
            while (true) {
                notesRepository.deleteOldNotesInTrash()

                if (prefsManager.shouldAutoExport &&
                    System.currentTimeMillis() - prefsManager.lastAutoExportTime >
                    PrefsManager.AUTO_EXPORT_DELAY.inWholeMilliseconds
                ) {
                    _autoExportEvent.send(prefsManager.autoExportUri)
                }

                delay(PERIODIC_TASK_INTERVAL.inWholeMilliseconds)
            }
        }
    }

    fun onStart() {
        viewModelScope.launch {
            notesRepository.deleteOldNotesInTrash()
        }
    }

    fun createNote(type: NoteType, title: String = "", content: String = "") {
        // Add note to database, then edit it.
        viewModelScope.launch {
            val date = Date()
            val note = Note(Note.NO_ID,
                type,
                title,
                content,
                when (type) {
                    NoteType.TEXT -> BlankNoteMetadata
                    NoteType.LIST -> ListNoteMetadata(List(content.lines().size) { false })
                },
                date,
                date,
                NoteStatus.ACTIVE,
                PinnedStatus.UNPINNED,
                null)
            val id = notesRepository.insertNote(note)
            _editNoteEvent.send(id)
        }
    }

    fun editNote(id: Long) {
        _editNoteEvent.send(id)
    }

    fun autoExport(output: OutputStream?) {
        if (output != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val jsonData = jsonManager.exportJsonData()
                prefsManager.autoExportFailed = try {
                    output.use {
                        output.write(jsonData.toByteArray())
                    }
                    prefsManager.lastAutoExportTime = System.currentTimeMillis()
                    false
                } catch (e: Exception) {
                    true
                }
            }
        } else {
            prefsManager.autoExportFailed = true
        }
    }

    companion object {
        private val PERIODIC_TASK_INTERVAL = Duration.hours(1)
    }
}
