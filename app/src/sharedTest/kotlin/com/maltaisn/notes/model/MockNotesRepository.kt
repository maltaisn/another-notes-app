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

import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    private val labels = mutableMapOf<Long, Label>()

    private val labelRefs = mutableMapOf<Long, Long>()

    var lastNoteId = 0L
        private set

    var lastLabelId = 0L
        private set

    /**
     * Last note that was added or updated in repository.
     */
    var lastAddedNote: Note? = null
        private set

    /**
     * Number of notes in database.
     */
    val notesCount: Int
        get() = notes.size

    /**
     * Number of labels in database.
     */
    val labelsCount: Int
        get() = labels.size

    private val noteChangeFlow = MutableSharedFlow<Unit>(replay = 1)
    private val labelChangeFlow = MutableSharedFlow<Unit>(replay = 1)

    private fun addNoteInternal(note: Note): Long {
        val id = if (note.id != Note.NO_ID) {
            notes[note.id] = note
            if (note.id > lastNoteId) {
                lastNoteId = note.id
            }
            note.id
        } else {
            lastNoteId++
            notes[lastNoteId] = note.copy(id = lastNoteId)
            lastNoteId
        }
        lastAddedNote = notes[id]
        return id
    }

    /** Non-suspending version of [insertNote]. */
    fun addNote(note: Note): Long {
        val id = addNoteInternal(note)
        noteChangeFlow.tryEmit(Unit)
        return id
    }

    override suspend fun insertNote(note: Note) = addNote(note)

    override suspend fun updateNote(note: Note) {
        require(note.id in notes) { "Cannot update non-existent note" }
        insertNote(note)
    }

    override suspend fun updateNotes(notes: List<Note>) {
        for (note in notes) {
            require(note.id != Note.NO_ID)
            addNoteInternal(note)
        }
        noteChangeFlow.emit(Unit)
    }

    override suspend fun deleteNote(note: Note) {
        deleteNote(note.id)
    }

    suspend fun deleteNote(id: Long) {
        notes -= id
        noteChangeFlow.emit(Unit)
    }

    override suspend fun deleteNotes(notes: List<Note>) {
        for (note in notes) {
            this.notes -= note.id
        }
        noteChangeFlow.emit(Unit)
    }

    override suspend fun getNoteById(id: Long) = notes[id]

    fun requireNoteById(id: Long) = notes.getOrElse(id) {
        error("No note with ID $id")
    }

    /**
     * Add label without notifying change flow.
     * Should only be used during test initialization!
     */
    fun addLabel(label: Label): Long {
        val id = if (label.id != Label.NO_ID) {
            labels[label.id] = label
            if (label.id > lastLabelId) {
                lastLabelId = label.id
            }
            label.id
        } else {
            lastLabelId++
            labels[lastLabelId] = label.copy(id = lastLabelId)
            lastLabelId
        }
        return id
    }

    override suspend fun insertLabel(label: Label): Long {
        val id = addLabel(label)
        labelChangeFlow.emit(Unit)
        return id
    }

    override suspend fun updateLabel(label: Label) {
        require(label.id in labels) { "Cannot update non-existent label" }
        insertLabel(label)
    }

    override suspend fun deleteLabel(label: Label) {
        labels -= label.id
        labelChangeFlow.emit(Unit)
    }

    override suspend fun getLabelById(id: Long) = labels[id]

    fun requireLabelById(id: Long) = labels.getOrElse(id) {
        error("No label with ID $id")
    }

    override suspend fun getLabelByName(name: String) = labels.values.find { it.name == name }

    override suspend fun insertLabelRefs(refs: List<LabelRef>) {
        for (ref in refs) {
            labelRefs[ref.noteId] = ref.labelId
        }
    }

    override suspend fun deleteLabelRefs(refs: List<LabelRef>) {
        for (ref in refs) {
            labelRefs -= ref.noteId
        }
    }

    override fun getNotesWithReminder() = noteChangeFlow.map {
        notes.values.asSequence()
            .filter { it.reminder?.done == false }
            .sortedBy { it.reminder!!.next.time }
            .toList()
    }

    override fun getNotesByStatus(status: NoteStatus) = noteChangeFlow.map {
        // Sort by last modified, then by ID.
        notes.values.asSequence()
            .filter { it.status == status }
            .sortedWith(compareByDescending<Note> { it.pinned }
                .thenByDescending { it.lastModifiedDate }
                .thenBy { it.id })
            .toList()
    }

    override fun searchNotes(query: String): Flow<List<Note>> {
        val queryNoFtsSyntax = query.replace("[*\"-]".toRegex(), "")
        return if (queryNoFtsSyntax.isEmpty()) {
            flow { emit(emptyList<Note>()) }
        } else {
            noteChangeFlow.map {
                val found = notes.mapNotNullTo(ArrayList()) { (_, note) ->
                    note.takeIf {
                        note.status != NoteStatus.DELETED &&
                                (queryNoFtsSyntax in note.title || queryNoFtsSyntax in note.content)
                    }
                }
                found.sortWith(compareBy<Note> { it.status }.thenByDescending { it.lastModifiedDate })
                found
            }
        }
    }

    override suspend fun emptyTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.DELETED
        }
        noteChangeFlow.emit(Unit)
    }

    override suspend fun deleteOldNotesInTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.DELETED &&
                    (System.currentTimeMillis() - note.lastModifiedDate.time) >
                    PrefsManager.TRASH_AUTO_DELETE_DELAY.toLongMilliseconds()
        }
        noteChangeFlow.emit(Unit)
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
        lastNoteId = 0
        noteChangeFlow.emit(Unit)
    }

    fun getAllNotes() = noteChangeFlow.map {
        notes.values.toList()
    }

    override fun getAllLabels() = noteChangeFlow.map {
        labels.values.toList()
    }

}
