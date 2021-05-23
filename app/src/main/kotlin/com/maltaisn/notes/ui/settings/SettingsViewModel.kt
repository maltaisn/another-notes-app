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

package com.maltaisn.notes.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val jsonManager: JsonManager,
) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    fun exportData(output: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = jsonManager.exportJsonData()
            try {
                output.use {
                    output.write(jsonData.toByteArray())
                }
                showMessage(R.string.export_success)
            } catch (e: IOException) {
                showMessage(R.string.export_fail)
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            notesRepository.clearAllData()
            labelsRepository.clearAllData()
            showMessage(R.string.pref_data_clear_success_message)
        }
    }

    private suspend fun showMessage(messageId: Int) = withContext(Dispatchers.Main) {
        _messageEvent.send(messageId)
    }
}
