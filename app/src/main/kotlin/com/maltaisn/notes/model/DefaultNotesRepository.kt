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

import com.maltaisn.notes.model.entity.DeletedNote
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.*


open class DefaultNotesRepository(
        private val notesDao: NotesDao,
        private val deletedNotesDao: DeletedNotesDao,
        private val json: Json
) : NotesRepository {

    override suspend fun insertNote(note: Note): Long = withContext(NonCancellable) {
        notesDao.insert(note)
    }

    override suspend fun updateNote(note: Note) = withContext(NonCancellable) {
        notesDao.update(note)
    }

    override suspend fun updateNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.updateAll(notes)
    }

    override suspend fun deleteNote(note: Note) = withContext(NonCancellable) {
        notesDao.delete(note)
        deletedNotesDao.insert(DeletedNote(0, note.uuid, false))
    }

    override suspend fun deleteNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.deleteAll(notes)
        deletedNotesDao.insertAll(notes.map { DeletedNote(0, it.uuid, false) })
    }

    override suspend fun getById(id: Long) = notesDao.getById(id)

    override fun getNotesByStatus(status: NoteStatus) = notesDao.getByStatus(status)

    override fun searchNotes(query: String) = notesDao.search(query)

    override suspend fun emptyTrash() {
        deleteNotes(getNotesByStatus(NoteStatus.TRASHED).first())
    }

    override suspend fun deleteOldNotesInTrash() {
        val delay = PrefsManager.TRASH_AUTO_DELETE_DELAY.toLongMilliseconds()
        val minDate = Date(System.currentTimeMillis() - delay)
        deleteNotes(notesDao.getByStatusAndDate(NoteStatus.TRASHED, minDate))
    }

    override suspend fun getJsonData(): String {
        val notesList = notesDao.getAll()
        val notesJson = JsonObject(notesList.associate { note ->
            note.uuid to json.toJson(Note.serializer(), note)
        })
        return json.stringify(JsonObject.serializer(), notesJson)
    }

    override suspend fun clearAllData() {
        // Just clear all tables. Sync flavor should provide a custom implementation.
        notesDao.clear()
        deletedNotesDao.clear()
    }

}
