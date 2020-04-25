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

import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.model.SyncManager
import com.maltaisn.notes.model.SyncNotesRepository
import com.maltaisn.notes.model.SyncPrefsManager
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject


class SyncLifecycleBehavior @Inject constructor(
        private val notesRepository: SyncNotesRepository,
        private val loginRepository: LoginRepository,
        private val syncManager: SyncManager,
        private val prefs: SyncPrefsManager
) : LifecycleBehavior {

    private var signedIn = false

    override suspend fun start() {
        loginRepository.authStateChannel.asFlow().collect {
            if (signedIn && loginRepository.currentUser == null) {
                // User signed out, either manually or by deleting the account.
                // All entities in database have their 'synced' property set to true, so if user
                // signs in from another account, no sync will happen! So synced must be set
                // to false for all entities, and reset last sync time.
                notesRepository.setAllNotSynced()
                prefs.lastSyncTime = SyncPrefsManager.NO_LAST_SYNC
            }
            signedIn = loginRepository.currentUser != null
        }
    }

    override suspend fun onStart() {
        // Sync notes when user opens the app.
        syncManager.syncNotes(delay = SyncPrefsManager.MIN_AUTO_SYNC_INTERVAL)
    }

    override suspend fun onStop() {
        // Sync notes when user closes the app. Remote changes are not wanted here
        // since sync will happen anyway when app starts next time.
        syncManager.syncNotes(receive = false)
    }

}
