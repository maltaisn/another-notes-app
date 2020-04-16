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
import androidx.core.content.edit
import com.maltaisn.notes.model.entity.DeletedNote
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.settings.PreferenceHelper
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class NotesRepository @Inject constructor(
        private val notesDao: NotesDao,
        private val deletedNotesDao: DeletedNotesDao,
        private val notesService: NotesService,
        private val prefs: SharedPreferences,
        private val json: Json) {

    private var lastSyncTime: Long
        get() = prefs.getLong(PreferenceHelper.LAST_SYNC_TIME, 0)
        set(value) {
            prefs.edit { putLong(PreferenceHelper.LAST_SYNC_TIME, value) }
        }


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
        deletedNotesDao.insert(DeletedNote(0, note.uuid, false))
    }

    suspend fun deleteNotes(notes: List<Note>) = withContext(NonCancellable) {
        notesDao.deleteAll(notes)
        deletedNotesDao.insertAll(notes.map { DeletedNote(0, it.uuid, false) })
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
     * @param receive Whether receiving remote notes is important or not. If not and there are no
     * local changes, no call will be made to the server.
     *
     * @throws IOException Thrown when sync fails.
     */
    open suspend fun syncNotes(receive: Boolean) {
        // Get local sync data.
        val localChanged = notesDao.getNotSynced()
        val localDeleted = deletedNotesDao.getNotSyncedUuids()

        if (!receive && localChanged.isEmpty() && localDeleted.isEmpty()) {
            // Don't receive remote remotes and no local changes, so no sync to do.
            return
        }

        // Send local changes to server, and receive remote changes
        val localData = NotesService.SyncData(Date(lastSyncTime), localChanged, localDeleted)
        val remoteData = notesService.syncNotes(localData)

        // Sync was successful, update "changed" flag and remove "deleted" notes from database.
        notesDao.setSyncedFlag(true)
        deletedNotesDao.clear()

        // Update local last sync time
        lastSyncTime = remoteData.lastSync.time

        // Update local notes.
        // Server doesn't return the 'synced' property, but it defaults to true.
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

        // Mark deleted UUIDs as synced.
        notesDao.setSyncedFlag(true)
    }

    /**
     * Set all notes and all deleted UUIDs as not synced.
     */
    suspend fun setAllNotSynced() {
        notesDao.setSyncedFlag(false)
        deletedNotesDao.setSyncedFlag(false)
    }

    suspend fun getJsonData(): String {
        val notesList = notesDao.getAll()
        val notesJson = JsonObject(notesList.associate { note ->
            note.uuid to json.toJson(Note.serializer(), note)
        })
        return json.stringify(JsonObject.serializer(), notesJson)
    }

    suspend fun clearAllData() {
        deleteNotes(notesDao.getAll())
    }

}
