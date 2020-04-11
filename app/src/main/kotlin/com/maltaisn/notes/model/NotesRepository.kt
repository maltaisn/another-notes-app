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

import android.content.SharedPreferences
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.model.entity.DeletedNote
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NotesRepository @Inject constructor(
        private val notesDao: NotesDao,
        private val deletedNotesDao: DeletedNotesDao,
        private val notesService: NotesService,
        private val prefs: SharedPreferences) {

    suspend fun insertNote(note: Note): Long = withContext(NonCancellable) {
        notesDao.insert(note)
    }

    suspend fun updateNote(note: Note) = withContext(NonCancellable) {
        notesDao.update(note)
    }

    suspend fun updateNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.updateAll(notes)
    }

    suspend fun deleteNote(note: Note) = withContext(NonCancellable) {
        notesDao.delete(note)
        deletedNotesDao.insert(DeletedNote(0, note.uuid))
    }

    suspend fun deleteNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.deleteAll(notes)
        deletedNotesDao.insertAll(notes.map { DeletedNote(0, it.uuid) })
    }

    suspend fun getById(id: Long) = notesDao.getById(id)

    fun getNotesByStatus(status: NoteStatus) = notesDao.getByStatus(status)

    fun searchNotes(query: String) = notesDao.search(query)

    suspend fun emptyTrash() {
        deleteNotes(getNotesByStatus(NoteStatus.TRASHED).first())
    }

    suspend fun deleteOldNotesInTrash() {
        val delay = PreferenceHelper.TRASH_AUTO_DELETE_DELAY.toLongMilliseconds()
        val minDate = Date(System.currentTimeMillis() - delay)
        deleteNotes(notesDao.getByStatusAndDate(NoteStatus.TRASHED, minDate))
    }

    /**
     * Sync local notes with the server. This happens in two steps:
     * 1. Local changes (inserted and updated notes, deleted notes UUIDs) are sent to the server.
     * 2. Server returns all remote changes since the last sync date (again, inserted and updated
     * notes, deleted notes UUIDs) and this data is updated locally.
     *
     * @throws IOException Thrown when sync fails.
     */
    suspend fun syncNotes(receive: Boolean = true) {
        // Get local sync data.
        val lastSyncTime = Date(prefs.getLong(PreferenceHelper.LAST_SYNC_TIME, 0))
        val localChanged = notesDao.getChanged()
        val localDeleted = deletedNotesDao.getAllUuids()

        if (!receive && localChanged.isEmpty() && localDeleted.isEmpty()) {
            // Don't receive remote remotes and no local changes, so no sync to do.
            return
        }

        // Send local changes to server, and receive remote changes
        val localData = NotesService.SyncData(lastSyncTime, localChanged, localDeleted)
        val remoteData = notesService.syncNotes(localData)

        // Sync was successful, update "changed" flag and remove "deleted" notes from database.
        notesDao.resetChangedFlag()
        deletedNotesDao.clear()

        // Update local last sync time
        prefs.edit().putLong(PreferenceHelper.LAST_SYNC_TIME, remoteData.lastSync.time).apply()

        // Update local notes
        val remotedChanged = remoteData.changedNotes.map { note ->
            val id = notesDao.getIdByUuid(note.uuid)
            if (id == null) {
                // No ID (added note).
                note
            } else {
                // ID found (updated note).
                note.copy(id = id)
            }
        }
        notesDao.insertAll(remotedChanged)

        // Delete local notes
        notesDao.deleteByUuid(remoteData.deletedUuids)
    }

}
