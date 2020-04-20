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

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.maltaisn.notes.ui.settings.PreferenceHelper
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration


@Singleton
class SyncManager @Inject constructor(
        private val context: Context,
        private val notesRepository: NotesRepository,
        private val loginRepository: LoginRepository,
        private val prefs: SharedPreferences) {

    private val canSyncOverCurrentNetwork: Boolean
        get() {
            val wifiNeeded = prefs.getBoolean(PreferenceHelper.SYNC_OVER_WIFI, false)
            return if (wifiNeeded) {
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;
                !manager.isActiveNetworkMetered
            } else {
                true
            }
        }

    /**
     * Sync notes if they haven't been synced since a certain [delay].
     * User must be signed in, has verified their email, and be connected to a valid network
     * connection (i.e. not metered if sync only over wifi setting is enabled.)
     *
     * @throws IOException If sync fails.
     * @see [NotesRepository.syncNotes].
     */
    suspend fun syncNotes(delay: Duration = Duration.ZERO,
                          receive: Boolean = true,
                          onError: (IOException) -> Unit = {}) {
        if (loginRepository.isUserSignedIn &&
                loginRepository.isUserEmailVerified &&
                canSyncOverCurrentNetwork) {
            val shouldSync = if (delay.isPositive()) {
                // Check if last sync time is within required delay.
                val lastSync = prefs.getLong(PreferenceHelper.LAST_SYNC_TIME, 0)
                val nextSyncTime = lastSync + delay.toLongMilliseconds()
                System.currentTimeMillis() >= nextSyncTime

            } else {
                // No delay specified.
                true
            }
            if (shouldSync) {
                try {
                    notesRepository.syncNotes(receive)
                } catch (e: IOException) {
                    // Sync failed for unknown reason.
                    onError(e)
                }
            }
        }
    }

}