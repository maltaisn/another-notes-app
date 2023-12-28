/*
 * Copyright 2023 Nicolas Maltais
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

package com.maltaisn.notesshared.model

import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteWithLabels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Implementation of the notes repository that stores data itself instead of relying on DAOs.
 * A [labelsRepository] is needed if using methods that return [NoteWithLabels].
 * This implementation should work almost exactly like [DefaultNotesRepository].
 *
 * There is most likely a way to use Room database from unit testing so this shouldn't be
 * really needed. At least it gives better introspection facilities by allowing to add test methods
 * accessing the data directly and non-suspending methods for convenience.
 *
 * Not supported:
 * - Notes with hidden labels.
 */
class MockNotesRepository(private val labelsRepository: MockLabelsRepository) : NotesRepository {

    private val notes = mutableMapOf<Long, Note>()

    var lastNoteId = 0L
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

    var sortField = SortField.MODIFIED_DATE
    var sortDirection = SortDirection.DESCENDING

    /**
     * Flow that emits when notes change.
     */
    private val changeFlow = MutableSharedFlow<Unit>(replay = 1)

    /**
     * Flow that emits when notes, labels, or label refs change.
     */
    private val noteWithLabelsChangeFlow =
        merge(changeFlow, labelsRepository.changeFlow, labelsRepository.refsChangeFlow)

    private val sortComparator: Comparator<Note>
        get() {
            val noteField = when (sortField) {
                SortField.ADDED_DATE -> Note::addedDate
                SortField.MODIFIED_DATE -> Note::lastModifiedDate
                SortField.TITLE -> { note -> note.title.lowercase() }
            }
            return when (sortDirection) {
                SortDirection.ASCENDING -> compareBy(noteField)
                SortDirection.DESCENDING -> compareByDescending(noteField)
            }
        }

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
        changeFlow.tryEmit(Unit)
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
        changeFlow.emit(Unit)
    }

    override suspend fun deleteNote(note: Note) {
        deleteNote(note.id)
    }

    private suspend fun deleteNoteInternal(id: Long) {
        notes -= id
        // delete all label refs for this note
        labelsRepository.deleteLabelRefs(labelsRepository.getNotesForLabelId(id)
            .map { LabelRef(id, it) })
    }

    suspend fun deleteNote(id: Long) {
        deleteNoteInternal(id)
        changeFlow.emit(Unit)
    }

    override suspend fun deleteNotes(notes: List<Note>) {
        for (note in notes) {
            deleteNoteInternal(note.id)
        }
        changeFlow.emit(Unit)
    }

    override suspend fun getNoteById(id: Long) = notes[id]

    override suspend fun getNoteByIdWithLabels(id: Long) =
        getNoteById(id)?.let(labelsRepository::getNoteWithLabels)

    fun requireNoteById(id: Long) = notes.getOrElse(id) {
        error("No note with ID $id")
    }

    override suspend fun getLastCreatedNote() = notes.values.maxByOrNull { it.addedDate }

    override fun getNotesWithReminder() = changeFlow.map {
        notes.values.asSequence()
            .filter { it.reminder?.done == false }
            .sortedBy { it.reminder!!.next.time }
            .map(labelsRepository::getNoteWithLabels)
            .toList()
    }.distinctUntilChanged()

    override fun getNotesByStatus(status: NoteStatus) = noteWithLabelsChangeFlow.map {
        // Sort by last modified, then by ID.
        notes.values.asSequence()
            .filter { it.status == status }
            .sortedWith(compareByDescending(Note::pinned)
                .thenBy(sortComparator) { it }
                .thenBy(Note::id))
            .map(labelsRepository::getNoteWithLabels)
            .toList()
    }.distinctUntilChanged()

    override fun getNotesByLabel(labelId: Long) = noteWithLabelsChangeFlow.map {
        labelsRepository.getNotesForLabelId(labelId).asSequence()
            .map { requireNoteById(it) }
            .filter { it.status != NoteStatus.DELETED }
            .sortedWith(compareBy(Note::status)
                .thenByDescending(Note::pinned)
                .thenBy(sortComparator) { it })
            .map(labelsRepository::getNoteWithLabels)
            .toList()
    }.distinctUntilChanged()

    override fun searchNotes(query: String): Flow<List<NoteWithLabels>> {
        val queryNoFtsSyntax = query.replace("[*\"-]".toRegex(), "")
        return if (queryNoFtsSyntax.isEmpty()) {
            flow { emit(emptyList<NoteWithLabels>()) }
        } else {
            noteWithLabelsChangeFlow.map {
                notes.values.asSequence()
                    .filter { it.status != NoteStatus.DELETED }
                    .filter { (queryNoFtsSyntax in it.title || queryNoFtsSyntax in it.content) }
                    .sortedWith(compareBy(Note::status)
                        .thenBy(sortComparator) { it })
                    .map(labelsRepository::getNoteWithLabels)
                    .toList()
            }.distinctUntilChanged()
        }
    }

    override suspend fun emptyTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.DELETED
        }
        changeFlow.emit(Unit)
    }

    override suspend fun deleteOldNotesInTrash() {
        notes.entries.removeIf { (_, note) ->
            note.status == NoteStatus.DELETED &&
                    (System.currentTimeMillis() - note.lastModifiedDate.time) > 7  // Actually this is now a setting...
        }
        changeFlow.emit(Unit)
    }

    override suspend fun clearAllData() {
        notes.clear()
        labelsRepository.clearAllLabelRefs()
        lastNoteId = 0
        changeFlow.emit(Unit)
    }

    fun getAllNotesWithLabels() = noteWithLabelsChangeFlow.map {
        notes.values.asSequence()
            .map(labelsRepository::getNoteWithLabels)
            .toList()
    }.distinctUntilChanged()
}
