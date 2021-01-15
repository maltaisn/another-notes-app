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

package com.maltaisn.notes.model

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Implementation of the notes repository that stores data itself instead of relying on DAOs.
 *
 * This implementation should work almost exactly like [DefaultNotesRepository].
 * Returned flows will also emit a new value on every change.
 */
class MockNotesRepository : NotesRepository {

    private val json = Json {}

    private val notes = mutableMapOf<Long, Note>()

    var lastId = 0L
        private set

    /**
     * Last note that was added or updated in repository.
     */
    var lastAddedNote: Note? = null
        private set

    /**
     * Number of notes in database.
     */
    val size: Int
        get() = notes.size

    private val changeChannel = ConflatedBroadcastChannel(Unit)

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
        changeChannel.sendBlocking(Unit)
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
        deleteNote(note.id)
    }

    suspend fun deleteNote(id: Long) {
        notes -= id
        changeChannel.send(Unit)
    }

    override suspend fun deleteNotes(notes: List<Note>) {
        for (note in notes) {
            deleteNote(note)
        }
    }

    override suspend fun getById(id: Long) = notes[id]

    override suspend fun getNotesWithReminder(): List<Note> {
        return notes.values.filter { it.reminder != null }
    }

    override fun getNotesByStatus(status: NoteStatus) = flow {
        changeChannel.asFlow().collect {
            // Sort by last modified, then by ID.
            val sorted = notes.values.sortedWith(
                compareByDescending<Note> { it.lastModifiedDate }.thenBy { it.id })
            emit(sorted.filter { it.status == status })
        }
    }

    override fun searchNotes(query: String) = flow {
        val queryNoFtsSyntax = query.replace("[*\"-]".toRegex(), "")
        if (queryNoFtsSyntax.isEmpty()) {
            emit(emptyList<Note>())
        } else {
            changeChannel.asFlow().collect {
                val found = notes.mapNotNullTo(ArrayList()) { (_, note) ->
                    note.takeIf {
                        note.status != NoteStatus.DELETED &&
                                (queryNoFtsSyntax in note.title || queryNoFtsSyntax in note.content)
                    }
                }
                found.sortWith(compareBy<Note> { it.status }.thenByDescending { it.lastModifiedDate })
                emit(found)
            }
        }
    }

    override suspend fun emptyTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.DELETED
        }
        changeChannel.send(Unit)
    }

    override suspend fun deleteOldNotesInTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.DELETED &&
                    (System.currentTimeMillis() - note.lastModifiedDate.time) >
                    PrefsManager.TRASH_AUTO_DELETE_DELAY.toLongMilliseconds()
        }
        changeChannel.send(Unit)
    }

    override suspend fun getJsonData(): String {
        val notesJson = buildJsonObject {
            for ((id, note) in notes) {
                put(id.toString(), json.encodeToJsonElement(Note.serializer(), note))
            }
        }
        return json.encodeToString(JsonObject.serializer(), notesJson)
    }

    override suspend fun clearAllData() {
        notes.clear()
        lastId = 0
        changeChannel.send(Unit)
    }

    fun getAll() = flow {
        changeChannel.asFlow().collect {
            emit(notes.values)
        }
    }
}
