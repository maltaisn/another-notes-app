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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class DefaultNotesRepository @Inject constructor(
    private val notesDao: NotesDao,
) : NotesRepository {

    // Data modification methods are wrapped in non-cancellable context
    // so that calling them in onPause for example won't cancel the transaction on the
    // subsequent onDestroy call, which cancels the coroutine scope.

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
    }

    override suspend fun deleteNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.deleteAll(notes)
    }

    override suspend fun getNoteById(id: Long) = notesDao.getById(id)


    override fun getNotesByStatus(status: NoteStatus) = notesDao.getByStatus(status)

    override fun getNotesWithReminder() = notesDao.getAllWithReminder()

    override fun searchNotes(query: String) = notesDao.search(query)

    override suspend fun emptyTrash() {
        deleteNotes(getNotesByStatus(NoteStatus.DELETED).first())
    }

    override suspend fun deleteOldNotesInTrash() {
        val delay = PrefsManager.TRASH_AUTO_DELETE_DELAY.toLongMilliseconds()
        val minDate = Date(System.currentTimeMillis() - delay)
        deleteNotes(notesDao.getByStatusAndDate(NoteStatus.DELETED, minDate))
    }

    override suspend fun clearAllData() {
        notesDao.clear()
    }
}
