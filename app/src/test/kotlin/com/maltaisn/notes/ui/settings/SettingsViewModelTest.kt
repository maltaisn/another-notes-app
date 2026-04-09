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

package com.maltaisn.notes.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.ArchiveExporter
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.ui.assertLiveDataEventSent
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

class SettingsViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `should release previous persistable uri when auto export is disabled`() {
        var autoExportUri = "content://com.example.documents/tree/notes.json"
        val prefsManager: PrefsManager = mock {
            on { autoExportUri } doAnswer { autoExportUri }
            on { disableAutoExport() } doAnswer { autoExportUri = "" }
        }

        val viewModel = SettingsViewModel(
            notesRepository = mock<NotesRepository>(),
            labelsRepository = mock<LabelsRepository>(),
            prefsManager = prefsManager,
            jsonManager = mock<JsonManager>(),
            archiveExporter = mock<ArchiveExporter>(),
            reminderAlarmManager = mock<ReminderAlarmManager>(),
            savedStateHandle = SavedStateHandle(),
        )

        viewModel.disableAutoExport()

        verify(prefsManager).disableAutoExport()
        assertEquals("", autoExportUri)
        assertLiveDataEventSent(viewModel.releasePersistableUriEvent, "content://com.example.documents/tree/notes.json")
    }
}
