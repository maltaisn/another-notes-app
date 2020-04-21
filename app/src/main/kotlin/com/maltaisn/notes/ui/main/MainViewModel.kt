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
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.SyncManager
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import com.maltaisn.notes.ui.settings.PreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.time.hours


class MainViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val loginRepository: LoginRepository,
        private val syncManager: SyncManager
) : ViewModel() {

    private var signedIn = false

    private val _editNoteEvent = MutableLiveData<Event<Long>>()
    val editItemEvent: LiveData<Event<Long>>
        get() = _editNoteEvent


    init {
        // Job to periodically remove old notes in trash
        viewModelScope.launch {
            while (true) {
                notesRepository.deleteOldNotesInTrash()
                delay(TRASH_AUTO_DELETE_INTERVAL.toLongMilliseconds())
            }
        }

        loginRepository.addAuthStateListener {
            if (signedIn && loginRepository.currentUser == null) {
                viewModelScope.launch {
                    // User signed out, either manually or by deleting the account.
                    // All entities in database have their 'synced' property set to true, so if user
                    // signs in from another account, no sync will happen! So synced must be set
                    // to false for all entities.
                    notesRepository.setAllNotSynced()
                }
            }
            signedIn = loginRepository.currentUser != null
        }
    }

    fun onStart() {
        viewModelScope.launch {
            notesRepository.deleteOldNotesInTrash()
            syncManager.syncNotes(delay = PreferenceHelper.MIN_AUTO_SYNC_INTERVAL)
        }
    }

    fun onStop() {
        viewModelScope.launch {
            syncManager.syncNotes(receive = false)
        }
    }

    fun addIntentNote(title: String, content: String) {
        // Add note to database, then edit it.
        viewModelScope.launch {
            val date = Date()
            val note = Note(Note.NO_ID, Note.generateNoteUuid(), NoteType.TEXT,
                    title, content, BlankNoteMetadata, date, date, NoteStatus.ACTIVE, false)
            val id = notesRepository.insertNote(note)
            _editNoteEvent.send(id)
        }
    }

    companion object {
        private val TRASH_AUTO_DELETE_INTERVAL = 1.hours
    }

}
