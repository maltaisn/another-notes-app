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

package com.maltaisn.notes.ui.sort

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.model.SortSettings
import com.maltaisn.notes.ui.assertLiveDataEventSent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class SortViewModelTest {

    private lateinit var viewModel: SortViewModel

    private lateinit var prefs: PrefsManager

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        prefs = mock {
            on { sortSettings } doReturn SortSettings(SortField.TITLE, SortDirection.ASCENDING)
        }

        viewModel = SortViewModel(prefs)
    }

    @Test
    fun `should set sort settings on start`() = runTest {
        viewModel.start()
        assertEquals(prefs.sortSettings, viewModel.sortSettings.value)
    }

    @Test
    fun `change sort field and set to ascending direction`() = runTest {
        viewModel.start()
        viewModel.changeSortField(SortField.MODIFIED_DATE)
        val newSettings = SortSettings(SortField.MODIFIED_DATE, SortDirection.ASCENDING)
        assertLiveDataEventSent(viewModel.sortSettingsChange, newSettings)
        assertEquals(newSettings, viewModel.sortSettings.value)
    }

    @Test
    fun `keep same sort field and set to descending direction`() = runTest {
        viewModel.start()
        viewModel.changeSortField(SortField.TITLE)
        val newSettings = SortSettings(SortField.TITLE, SortDirection.DESCENDING)
        assertLiveDataEventSent(viewModel.sortSettingsChange, newSettings)
        assertEquals(newSettings, viewModel.sortSettings.value)
    }

    @Test
    fun `keep custom field and not change direction`() = runTest {
        viewModel.start()
        viewModel.changeSortField(SortField.CUSTOM)
        val newSettings = SortSettings(SortField.CUSTOM, SortDirection.ASCENDING)
        assertLiveDataEventSent(viewModel.sortSettingsChange, newSettings)
        assertEquals(newSettings, viewModel.sortSettings.value)
    }
}
