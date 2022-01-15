/*
 * Copyright 2022 Nicolas Maltais
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultNotesRepository @Inject constructor(
    private val notesDao: NotesDao,
    private val prefs: PrefsManager,
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

    override suspend fun getNoteByIdWithLabels(id: Long) = notesDao.getByIdWithLabels(id)

    override suspend fun getLastCreatedNote() = notesDao.getLastCreatedNote()

    override fun getNotesByStatus(status: NoteStatus) = notesDao.getByStatus(status, prefs.sortSettings)

    override fun getNotesByLabel(labelId: Long) = notesDao.getByLabel(labelId, prefs.sortSettings)

    override fun getNotesWithReminder() = notesDao.getAllWithReminder()

    override fun searchNotes(query: String) = notesDao.search(query, prefs.sortSettings)

    override suspend fun emptyTrash() {
        withContext(NonCancellable) {
            notesDao.deleteNotesByStatusAndDate(NoteStatus.DELETED, Long.MAX_VALUE)
        }
    }

    override suspend fun deleteOldNotesInTrash() {
        val delay = PrefsManager.TRASH_AUTO_DELETE_DELAY.inWholeMilliseconds
        val minDate = System.currentTimeMillis() - delay
        notesDao.deleteNotesByStatusAndDate(NoteStatus.DELETED, minDate)
    }

    override suspend fun clearAllData() {
        notesDao.clear()
    }
}
