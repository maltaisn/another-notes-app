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

package com.maltaisn.notes.ui.edit

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class EditViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val prefs: SharedPreferences) : ViewModel() {

    private var noteStatus = NoteStatus.ACTIVE
    private var noteId = Note.NO_ID


    private val _noteType = MutableLiveData<NoteType>()
    val noteType: LiveData<NoteType>
        get() = _noteType


    fun start(noteStatus: NoteStatus, noteId: Long) {
        this.noteStatus = noteStatus
        this.noteId = noteId

        viewModelScope.launch {
            // If note is null, that means a note is being created, default to text type.
            val note = notesRepository.getById(noteId)
            _noteType.value = note?.type ?: NoteType.TEXT
        }
    }

    fun toggleNoteType() {
        _noteType.value = when (_noteType.value!!) {
            NoteType.TEXT -> {
                // TODO convert to text
                NoteType.LIST
            }
            NoteType.LIST -> {
                // TODO convert to list
                NoteType.TEXT
            }
        }
    }

    fun moveNote() {
        if (noteStatus == NoteStatus.ACTIVE) {
            // TODO move to archived
        } else {
            // TODO move to active
        }
    }

    fun copyNote() {
        // TODO make a copy
    }

    fun shareNote() {
        // TODO start share intent
    }

    fun deleteNote() {
        // TODO delete note
    }

}
