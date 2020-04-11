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

package com.maltaisn.notes.ui.main

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.model.NotesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import kotlin.time.hours


class MainViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val loginRepository: LoginRepository,
        private val prefs: SharedPreferences
) : ViewModel() {

    init {
        // Job to periodically remove old notes in trash
        viewModelScope.launch {
            while (true) {
                notesRepository.deleteOldNotesInTrash()
                delay(TRASH_AUTO_DELETE_INTERVAL.toLongMilliseconds())
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            notesRepository.deleteOldNotesInTrash()
            syncNotesIfNeeded()
        }
    }

    fun onPause() {
        viewModelScope.launch {
            // Send changes to server if needed.
            notesRepository.syncNotes(receive = false)
        }
    }

    private fun syncNotesIfNeeded() {
        if (loginRepository.isUserSignedIn) {
            // Sync notes if last sync was beyond delay for automatic syncing.
            val lastSync = prefs.getLong(PreferenceHelper.LAST_SYNC_TIME, 0)
            val nextSyncTime = lastSync + PreferenceHelper.MIN_AUTO_SYNC_INTERVAL.toLongMilliseconds()
            if (System.currentTimeMillis() >= nextSyncTime) {
                viewModelScope.launch {
                    try {
                        notesRepository.syncNotes()
                    } catch (e: IOException) {
                        // Sync failed for unknown reason.
                    }
                }
            }
        }
    }

    companion object {
        private val TRASH_AUTO_DELETE_INTERVAL = 1.hours
    }

}
