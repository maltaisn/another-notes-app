/*
 * Copyright 2025 Nicolas Maltais
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
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.AppTheme
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.TrashCleanDelay
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import org.jetbrains.annotations.TestOnly

/**
 * Preference manager interface, to allow mocks.
 * Actual implementation in [DefaultPrefsManager].
 */
interface PrefsManager {

    val theme: AppTheme
    val dynamicColors: Boolean
    val strikethroughChecked: Boolean
    val moveCheckedToBottom: Boolean
    var listLayoutMode: NoteListLayoutMode
    val swipeActionLeft: SwipeAction
    val swipeActionRight: SwipeAction
    val shownDateField: ShownDateField
    val maximumPreviewLabels: Int
    val trashCleanDelay: TrashCleanDelay

    var sortField: SortField
    var sortDirection: SortDirection

    var shouldEncryptExportedData: Boolean
    var encryptedExportKeyDerivationSalt: String
    var encryptedImportKeyDerivationSalt: String
    var shouldAutoExport: Boolean
    var autoExportUri: String
    var autoExportFailed: Boolean
    var lastAutoExportTime: Long

    var lastTrashReminderTime: Long
    var lastRestrictedBatteryReminderTime: Long

    val sortSettings: SortSettings
        get() = SortSettings(sortField, sortDirection)

    fun getMaximumPreviewLines(noteType: NoteType): Int

    fun setDefaults(context: Context)

    fun disableAutoExport()

    fun migratePreferences()

    @TestOnly
    fun clear(context: Context)
}

