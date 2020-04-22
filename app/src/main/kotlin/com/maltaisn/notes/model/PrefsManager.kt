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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.AppTheme
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import javax.inject.Inject
import kotlin.time.days
import kotlin.time.minutes
import kotlin.time.seconds


open class PrefsManager @Inject constructor(private val prefs: SharedPreferences) {

    val theme: AppTheme
        get() {
            val value = prefs.getString(THEME, AppTheme.SYSTEM.value)
            return AppTheme.values().find { it.value == value }!!
        }

    var listLayoutMode: NoteListLayoutMode
        get() {
            val value = prefs.getInt(LIST_LAYOUT_MODE, NoteListLayoutMode.LIST.value)
            return NoteListLayoutMode.values().find { it.value == value }!!
        }
        set(value) {
            prefs.edit { putInt(LIST_LAYOUT_MODE, value.value) }
        }

    var lastTrashReminderTime: Long
        get() = prefs.getLong(LAST_TRASH_REMIND_TIME, 0)
        set(value) = prefs.edit { putLong(LAST_TRASH_REMIND_TIME, value) }

    open val shouldSyncOverWifi: Boolean
        get() = prefs.getBoolean(SYNC_OVER_WIFI, false)

    open var lastSyncTime: Long
        get() = prefs.getLong(LAST_SYNC_TIME, 0)
        set(value) = prefs.edit { putLong(LAST_SYNC_TIME, value) }


    fun setDefaults(context: Context) {
        PreferenceManager.setDefaultValues(context, R.xml.prefs, false)
    }


    companion object {
        // Settings keys
        const val THEME = "theme"
        const val SYNC_OVER_WIFI = "sync_over_wifi"
        const val EXPORT_DATA = "export_data"
        const val CLEAR_DATA = "clear_data"
        const val PRIVACY_POLICY = "privacy_policy"
        const val VIEW_SOURCE = "view_source"
        const val VIEW_LICENSES = "view_licenses"
        const val VERSION = "version"

        // Other keys
        private const val LAST_SYNC_TIME = "last_sync_time"
        private const val LIST_LAYOUT_MODE = "is_in_list_layout"
        private const val LAST_TRASH_REMIND_TIME = "last_deleted_remind_time"

        // Values
        const val VIEW_SOURCE_URL = "http://example.com/"

        val MIN_AUTO_SYNC_INTERVAL = 10.minutes
        val MIN_MANUAL_SYNC_INTERVAL = 15.seconds

        val TRASH_AUTO_DELETE_DELAY = 7.days
        val TRASH_REMINDER_DELAY = 60.days
    }

}
