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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import kotlin.time.hours

class MainViewModel @Inject constructor(
    private val notesRepository: NotesRepository
) : ViewModel() {

    private val _editNoteEvent = MutableLiveData<Event<Long>>()
    val editItemEvent: LiveData<Event<Long>>
        get() = _editNoteEvent

    init {
        viewModelScope.launch {
            // Periodically remove old notes in trash
            while (true) {
                notesRepository.deleteOldNotesInTrash()
                delay(TRASH_AUTO_DELETE_INTERVAL.toLongMilliseconds())
            }
        }
    }

    fun onStart() {
        viewModelScope.launch {
            notesRepository.deleteOldNotesInTrash()
        }
    }

    fun addIntentNote(title: String, content: String) {
        // Add note to database, then edit it.
        viewModelScope.launch {
            val date = Date()
            val note = Note(Note.NO_ID,
                NoteType.TEXT,
                title,
                content,
                BlankNoteMetadata,
                date,
                date,
                NoteStatus.ACTIVE,
                PinnedStatus.UNPINNED,
                null)
            val id = notesRepository.insertNote(note)
            _editNoteEvent.send(id)
        }
    }

    companion object {
        private val TRASH_AUTO_DELETE_INTERVAL = 1.hours
    }
}
