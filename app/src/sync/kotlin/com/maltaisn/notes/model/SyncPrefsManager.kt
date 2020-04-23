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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.minutes
import kotlin.time.seconds


@Singleton
open class SyncPrefsManager @Inject constructor(prefs: SharedPreferences) : PrefsManager(prefs) {

    open val shouldSyncOverWifi: Boolean
        get() = prefs.getBoolean(SYNC_OVER_WIFI, false)

    open var lastSyncTime: Long
        get() = prefs.getLong(LAST_SYNC_TIME, NO_LAST_SYNC)
        set(value) = prefs.edit { putLong(LAST_SYNC_TIME, value) }


    companion object {
        // Settings keys
        const val SYNC_OVER_WIFI = "sync_over_wifi"

        // Other keys
        private const val LAST_SYNC_TIME = "last_sync_time"

        // Values
        val MIN_AUTO_SYNC_INTERVAL = 10.minutes
        val MIN_MANUAL_SYNC_INTERVAL = 15.seconds

        const val NO_LAST_SYNC = 0L
    }

}
