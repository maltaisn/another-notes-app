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

package com.maltaisn.notes.model

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject


/**
 * Implementation of the notes repository that stores data itself instead of relying on DAOs.
 */
class MockNotesRepository : NotesRepository {

    private val json = Json(JsonConfiguration.Stable)

    private val notes = mutableMapOf<Long, Note>()

    var lastId = 0L
        private set

    /**
     * Last note that was added or updated in repository.
     */
    var lastAddedNote: Note? = null
        private set


    fun addNote(note: Note): Long {
        val id = if (note.id != Note.NO_ID) {
            notes[note.id] = note
            if (note.id > lastId) {
                lastId = note.id
            }
            note.id
        } else {
            lastId++
            notes[lastId] = note.copy(id = lastId)
            lastId
        }
        lastAddedNote = notes[id]
        return id
    }

    override suspend fun insertNote(note: Note) = addNote(note)


    override suspend fun updateNote(note: Note) {
        require(note.id != Note.NO_ID)
        insertNote(note)
    }

    override suspend fun updateNotes(notes: List<Note>) {
        for (note in notes) {
            updateNote(note)
        }
    }

    override suspend fun deleteNote(note: Note) {
        notes.remove(note.id)
    }

    override suspend fun deleteNotes(notes: List<Note>) {
        for (note in notes) {
            deleteNote(note)
        }
    }

    override suspend fun getById(id: Long) = notes[id]

    override fun getNotesByStatus(status: NoteStatus) = flow {
        emit(notes.mapNotNull { (_, note) ->
            note.takeIf { note.status == status }
        })
    }

    override fun searchNotes(query: String) = flow {
        emit(notes.mapNotNull { (_, note) ->
            note.takeIf { query in note.title || query in note.content }
        })
    }

    override suspend fun emptyTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.TRASHED
        }
    }

    override suspend fun deleteOldNotesInTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.TRASHED &&
                    (System.currentTimeMillis() - note.lastModifiedDate.time) >
                    PrefsManager.TRASH_AUTO_DELETE_DELAY.toLongMilliseconds()
        }
    }

    override suspend fun getJsonData(): String {
        val notesJson = JsonObject(notes.values.associate { note ->
            note.uuid to json.toJson(Note.serializer(), note)
        })
        return json.stringify(JsonObject.serializer(), notesJson)
    }

    override suspend fun clearAllData() {
        notes.clear()
        lastId = 0
    }

}
