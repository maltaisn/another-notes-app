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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.model.SortSettings
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SortViewModel @Inject constructor(
    private val prefs: PrefsManager,
) : ViewModel() {

    private val _sortSettings = MutableLiveData<SortSettings>()
    val sortSettings: LiveData<SortSettings>
        get() = _sortSettings

    private val _sortSettingsChange = MutableLiveData<Event<SortSettings>>()
    val sortSettingsChange: LiveData<Event<SortSettings>>
        get() = _sortSettingsChange

    fun start() {
        _sortSettings.value = prefs.sortSettings
    }

    fun changeSortField(field: SortField) {
        val settings = prefs.sortSettings

        val direction: SortDirection
        if (settings.field == field && field != SortField.CUSTOM) {
            // Field selected again, reverse sort direction
            direction = when (settings.direction) {
                SortDirection.ASCENDING -> SortDirection.DESCENDING
                SortDirection.DESCENDING -> SortDirection.ASCENDING
            }
        } else {
            direction = SortDirection.ASCENDING
        }

        val newSettings = SortSettings(field, direction)
        _sortSettings.value = newSettings
        _sortSettingsChange.send(newSettings)
    }
}
