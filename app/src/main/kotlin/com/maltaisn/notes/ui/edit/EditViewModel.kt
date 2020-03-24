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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject


class EditViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val json: Json) : ViewModel() {

    private var note: Note? = null


    private val _noteType = MutableLiveData<NoteType?>()
    val noteType: LiveData<NoteType?>
        get() = _noteType

    private val _noteStatus = MutableLiveData<NoteStatus?>()
    val noteStatus: LiveData<NoteStatus?>
        get() = _noteStatus


    fun start(noteId: Long) {
        this.note = null
        viewModelScope.launch {
            // Try to get note by ID.
            var note = notesRepository.getById(noteId)
            if (note == null) {
                // Note doesn't exist, create new blank text note.
                val date = Date()
                note = Note(Note.NO_ID, generateNoteUuid(), NoteType.TEXT,
                        "", "", null, date, date, NoteStatus.ACTIVE)
                val id = notesRepository.insertNote(note)
                note = note.copy(id = id)
            }
            this@EditViewModel.note = note

            _noteType.value = note.type
            _noteStatus.value = note.status
        }
    }

    fun exit() {
        val note = note ?: return
        if (note.isBlank) {
            // Discard blank note.
            viewModelScope.launch {
                notesRepository.deleteNote(note)
            }
        }
    }

    fun toggleNoteType() {
        val note = note ?: return
        val newType = when (note.type) {
            NoteType.TEXT -> NoteType.LIST
            NoteType.LIST -> NoteType.TEXT
        }
        _noteType.value = newType
        this.note = note.convertToType(newType, json)
        // TODO update UI
    }

    fun moveNote() {
        val note = note ?: return
        changeNoteStatus(if (note.status == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            // Unarchive or restore
            NoteStatus.ACTIVE
        })
        // TODO show snackbar with undo
    }

    fun copyNote() {
        val note = note ?: return
        val date = Date()
        val copy = note.copy(
                id = Note.NO_ID,
                uuid = generateNoteUuid(),
                addedDate = date,
                lastModifiedDate = date
        )
        viewModelScope.launch {
            notesRepository.insertNote(copy)
        }

        // Edit copy, not original.
        this.note = copy

        // TODO append localized " - Copy" to the note to make it evident user has created copy
    }

    fun shareNote() {
        // TODO start share intent
    }

    fun deleteNote() {
        val note = note ?: return
        viewModelScope.launch {
            if (noteStatus.value == NoteStatus.TRASHED) {
                // Delete forever
                notesRepository.deleteNote(note)
            } else {
                // Send to trash
                changeNoteStatus(NoteStatus.TRASHED)
            }
        }
        // TODO show snackbar with undo
    }

    private fun changeNoteStatus(newStatus: NoteStatus) {
        val note = note?.copy(
                status = newStatus,
                lastModifiedDate = Date()
        ) ?: return
        viewModelScope.launch {
            notesRepository.updateNote(note)
        }
    }

    private fun generateNoteUuid() = UUID.randomUUID().toString().replace("-", "")

}
