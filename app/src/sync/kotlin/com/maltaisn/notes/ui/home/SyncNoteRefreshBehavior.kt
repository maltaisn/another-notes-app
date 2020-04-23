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

package com.maltaisn.notes.ui.home

import android.util.Log
import com.maltaisn.notes.R
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.model.SyncManager
import com.maltaisn.notes.model.SyncPrefsManager
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject


class SyncNoteRefreshBehavior @Inject constructor(
        private val loginRepository: LoginRepository,
        private val syncManager: SyncManager
) : NoteRefreshBehavior() {

    override suspend fun start() {
        loginRepository.authStateChannel.asFlow().collect {
            canRefreshChannel.send(loginRepository.isUserSignedIn)
        }
    }

    override suspend fun refreshNotes(): Int? {
        var message: Int? = null
        syncManager.syncNotes(delay = SyncPrefsManager.MIN_MANUAL_SYNC_INTERVAL) { e ->
            // Sync failed for unknown reason.
            Log.e(TAG, "Couldn't sync notes", e)
            message = R.string.sync_failed_message
        }
        return message
    }

    companion object {
        private val TAG = NoteRefreshBehavior::class.java.simpleName
    }

}
