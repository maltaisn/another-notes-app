/*
 * Copyright 2021 Nicolas Maltais
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
import com.maltaisn.notes.OpenForTesting
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.AppTheme
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import javax.inject.Inject
import kotlin.time.days

/**
 * Base preference manager. This class interacts with [SharedPreferences]
 * so that other classes don't need knowledge of the keys and their associated type.
 *
 * Flavors provide their own extension of this manager.
 */
@OpenForTesting
class PrefsManager @Inject constructor(
    private val prefs: SharedPreferences
) {

    val theme: AppTheme
        get() {
            val value = prefs.getString(THEME, AppTheme.SYSTEM.value)
            return AppTheme.values().find { it.value == value }!!
        }

    val strikethroughChecked: Boolean
        get() = prefs.getBoolean(STRIKETHROUGH_CHECKED, true)

    var listLayoutMode: NoteListLayoutMode
        get() {
            val value = prefs.getInt(LIST_LAYOUT_MODE, NoteListLayoutMode.LIST.value)
            return NoteListLayoutMode.values().find { it.value == value }!!
        }
        set(value) {
            prefs.edit { putInt(LIST_LAYOUT_MODE, value.value) }
        }

    val swipeAction: SwipeAction
        get() {
            val value = prefs.getString(SWIPE_ACTION, SwipeAction.ARCHIVE.value)
            return SwipeAction.values().find { it.value == value }!!
        }

    val shownDateField: ShownDateField
        get() {
            val value = prefs.getString(SHOWN_DATE, ShownDateField.NONE.value)
            return ShownDateField.values().find { it.value == value }!!
        }

    var lastTrashReminderTime: Long
        get() = prefs.getLong(LAST_TRASH_REMIND_TIME, 0)
        set(value) = prefs.edit { putLong(LAST_TRASH_REMIND_TIME, value) }

    var lastRestrictedBatteryReminderTime: Long
        get() = prefs.getLong(LAST_RESTRICTED_BATTERY_REMIND_TIME, 0)
        set(value) = prefs.edit { putLong(LAST_RESTRICTED_BATTERY_REMIND_TIME, value) }

    fun getMaximumPreviewLines(layoutMode: NoteListLayoutMode, noteType: NoteType): Int {
        val key = when (layoutMode) {
            NoteListLayoutMode.LIST -> when (noteType) {
                NoteType.TEXT -> PREVIEW_LINES_TEXT_LIST
                NoteType.LIST -> PREVIEW_LINES_LIST_LIST
            }
            NoteListLayoutMode.GRID -> when (noteType) {
                NoteType.TEXT -> PREVIEW_LINES_TEXT_GRID
                NoteType.LIST -> PREVIEW_LINES_LIST_GRID
            }
        }
        return prefs.getInt(key, 0)
    }

    fun setDefaults(context: Context) {
        for (prefsRes in PREFS_XML) {
            // since there are multiple preferences files, readAgain must be true, otherwise
            // the first call to setDefaultValues marks preferences as read, so subsequent calls
            // will have no effect (or that's what I presumed at least, since it didn't work).
            PreferenceManager.setDefaultValues(context, prefsRes, true)
        }
    }

    companion object {
        // Settings keys
        const val THEME = "theme"
        const val PREVIEW_LINES = "preview_lines"
        const val PREVIEW_LINES_TEXT_LIST = "preview_lines_text_list"
        const val PREVIEW_LINES_LIST_LIST = "preview_lines_list_list"
        const val PREVIEW_LINES_TEXT_GRID = "preview_lines_text_grid"
        const val PREVIEW_LINES_LIST_GRID = "preview_lines_list_grid"
        const val STRIKETHROUGH_CHECKED = "strikethrough_checked"
        const val SHOWN_DATE = "shown_date"
        const val SWIPE_ACTION = "swipe_action"
        const val EXPORT_DATA = "export_data"
        const val CLEAR_DATA = "clear_data"
        const val VIEW_LICENSES = "view_licenses"
        const val VERSION = "version"

        // Other keys
        private const val LIST_LAYOUT_MODE = "is_in_list_layout"
        private const val LAST_TRASH_REMIND_TIME = "last_deleted_remind_time"
        private const val LAST_RESTRICTED_BATTERY_REMIND_TIME = "last_restricted_battery_remind_time"

        private val PREFS_XML = listOf(
            R.xml.prefs,
            R.xml.prefs_preview_lines,
        )

        /**
         * Delay after which notes in trash are automatically deleted forever.
         */
        val TRASH_AUTO_DELETE_DELAY = 7.days

        /**
         * Required delay before showing the trash reminder delay after user dismisses it.
         */
        val TRASH_REMINDER_DELAY = 60.days

        /**
         * Required delay before showing a notice that restricted battery mode will impact
         * reminders, after user dismisses it.
         */
        val RESTRICTED_BATTERY_REMINDER_DELAY = 60.days
    }
}
