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
import com.maltaisn.notes.model.entity.ChangeEvent
import com.maltaisn.notes.model.entity.ChangeEventType
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NotesRepository @Inject constructor(
        private val notesDao: NotesDao,
        private val changeEventsDao: ChangeEventsDao,
        private val notesService: NotesService,
        private val prefs: SharedPreferences) {

    suspend fun createNote(note: Note) {
        notesDao.insert(note)
        addChangeEvent(note, ChangeEventType.ADDED)
    }

    suspend fun updateNote(note: Note) {
        notesDao.update(note)
        addChangeEvent(note, ChangeEventType.UPDATED)
    }

    suspend fun deleteNote(note: Note) {
        notesDao.delete(note)
        addChangeEvent(note, ChangeEventType.DELETED)
    }

    private suspend fun addChangeEvent(note: Note, type: ChangeEventType) {
        changeEventsDao.insert(ChangeEvent(note.uuid, type))
    }

    suspend fun searchNotes(query: String) = notesDao.search(query)

    suspend fun getNotesByStatus(status: NoteStatus) = notesDao.getByStatus(status)

    suspend fun syncNotes() {
        // Create a list of local changes to send
        val localChanges = changeEventsDao.getAll().map { event ->
            NotesService.ChangeEventData(event.uuid, notesDao.getByUuid(event.uuid), event.type)
        }
        val lastSyncTime = Date(prefs.getLong(PreferenceHelper.LAST_SYNC_TIME, 0))
        val localData = NotesService.SyncData(lastSyncTime, localChanges)

        // Send local changes to server, and receive remote changes
        val remoteData = try {
            notesService.syncNotes(localData)
        } catch (e: IOException) {
            throw IOException("Sync notes failed", e)
        }

        // Clear local change events that were just synced.
        changeEventsDao.clear()

        // Update local notes
        for (event in remoteData.events) {
            when (event.type) {
                ChangeEventType.ADDED, ChangeEventType.UPDATED -> {
                    // Set existing ID on note if UUID already exists in database.
                    // Otherwise use ID 0 so that it will be auto-generated.
                    val note = event.note!!
                    val id = notesDao.getIdByUuid(note.uuid) ?: 0
                    notesDao.insert(note.copy(id = id))
                }
                ChangeEventType.DELETED -> notesDao.delete(event.uuid)
            }
        }

        // Update last sync time
        prefs.edit().putLong(PreferenceHelper.LAST_SYNC_TIME, remoteData.lastSync.time).apply()
    }

}
