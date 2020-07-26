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

package com.maltaisn.notes.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val notesRepository: NotesRepository
) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _exportDataEvent = MutableLiveData<Event<String>>()
    val exportDataEvent: LiveData<Event<String>>
        get() = _exportDataEvent

    fun exportData() {
        viewModelScope.launch {
            _exportDataEvent.send(notesRepository.getJsonData())
        }
    }

    fun clearData() {
        viewModelScope.launch {
            notesRepository.clearAllData()
            _messageEvent.send(R.string.pref_data_clear_success_message)
        }
    }
}
