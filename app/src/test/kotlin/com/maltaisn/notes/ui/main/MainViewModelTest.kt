/*
 * Copyright 2026 Nicolas Maltais
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.ArchiveExporter
import com.maltaisn.notes.model.AutoExportFormat
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `should auto export json data`() {
        var lastAutoExportTime = 0L
        var autoExportFailed = true
        val prefs: PrefsManager = mock {
            on { shouldAutoExport } doReturn false
            on { autoExportFormat } doReturn AutoExportFormat.JSON
            on { lastAutoExportTime } doAnswer { lastAutoExportTime }
            on { lastAutoExportTime = any() } doAnswer {
                lastAutoExportTime = it.arguments[0] as Long
            }
            on { autoExportFailed = any() } doAnswer {
                autoExportFailed = it.arguments[0] as Boolean
            }
        }
        val output = CountingByteArrayOutputStream()
        val viewModel = createViewModel(
            prefs = prefs,
            jsonManager = object : JsonManager {
                override suspend fun exportJsonData() = "{\"notes\":[]}"
                override suspend fun importJsonData(data: String, importKey: javax.crypto.SecretKey?) =
                    JsonManager.ImportResult.SUCCESS
            },
        )

        viewModel.autoExport(output, "Untitled")

        assertTrue(output.closedLatch.await(2, TimeUnit.SECONDS))
        assertContentEquals("{\"notes\":[]}".toByteArray(), output.toByteArray())
        assertFalse(autoExportFailed)
        assertTrue(lastAutoExportTime > 0)
    }

    @Test
    fun `should auto export zip archive`() {
        var lastAutoExportTime = 0L
        var autoExportFailed = true
        val prefs: PrefsManager = mock {
            on { shouldAutoExport } doReturn false
            on { autoExportFormat } doReturn AutoExportFormat.ZIP
            on { lastAutoExportTime } doAnswer { lastAutoExportTime }
            on { lastAutoExportTime = any() } doAnswer {
                lastAutoExportTime = it.arguments[0] as Long
            }
            on { autoExportFailed = any() } doAnswer {
                autoExportFailed = it.arguments[0] as Boolean
            }
        }
        val output = CountingByteArrayOutputStream()
        val viewModel = createViewModel(
            prefs = prefs,
            archiveExporter = object : ArchiveExporter {
                override suspend fun exportArchive(output: java.io.OutputStream, untitledName: String) {
                    output.write("ZIPDATA".toByteArray())
                }
            },
        )

        viewModel.autoExport(output, "Untitled")

        assertTrue(output.closedLatch.await(2, TimeUnit.SECONDS))
        assertContentEquals("ZIPDATA".toByteArray(), output.toByteArray())
        assertFalse(autoExportFailed)
        assertTrue(lastAutoExportTime > 0)
    }

    private fun createViewModel(
        prefs: PrefsManager,
        jsonManager: JsonManager = object : JsonManager {
            override suspend fun exportJsonData() = ""
            override suspend fun importJsonData(data: String, importKey: javax.crypto.SecretKey?) =
                JsonManager.ImportResult.SUCCESS
        },
        archiveExporter: ArchiveExporter = object : ArchiveExporter {
            override suspend fun exportArchive(output: java.io.OutputStream, untitledName: String) = Unit
        },
    ) = MainViewModel(
        notesRepository = MockNotesRepository(MockLabelsRepository()),
        labelsRepository = MockLabelsRepository(),
        prefsManager = prefs,
        jsonManager = jsonManager,
        archiveExporter = archiveExporter,
        reminderAlarmManager = object : ReminderAlarmManager {
            override suspend fun updateAllAlarms() = Unit
            override fun setNoteReminderAlarm(note: com.maltaisn.notes.model.entity.Note) = Unit
            override suspend fun setNextNoteReminderAlarm(note: com.maltaisn.notes.model.entity.Note) = Unit
            override suspend fun markReminderAsDone(noteId: Long) = Unit
            override fun removeAlarm(noteId: Long) = Unit
            override suspend fun removeAllAlarms() = Unit
        },
        savedStateHandle = SavedStateHandle(),
    )

    private class CountingByteArrayOutputStream : ByteArrayOutputStream() {
        val closedLatch = CountDownLatch(1)

        override fun close() {
            super.close()
            closedLatch.countDown()
        }
    }
}
