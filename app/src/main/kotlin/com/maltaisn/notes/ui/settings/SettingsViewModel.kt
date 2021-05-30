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
import com.maltaisn.notes.model.DefaultJsonManager.ImportResult
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefsManager: PrefsManager,
    private val jsonManager: JsonManager,
) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _lastAutoExport = MutableLiveData<Long>()
    val lastAutoExport: LiveData<Long>
        get() = _lastAutoExport

    private val _releasePersistableUriEvent = MutableLiveData<Event<String>>()
    val releasePersistableUriEvent: LiveData<Event<String>>
        get() = _releasePersistableUriEvent

    init {
        _lastAutoExport.value = prefsManager.lastAutoExportTime
    }

    fun exportData(output: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = jsonManager.exportJsonData()
            try {
                output.use {
                    output.write(jsonData.toByteArray())
                }
                showMessage(R.string.export_success)
            } catch (e: Exception) {
                showMessage(R.string.export_fail)
            }
        }
    }

    fun setupAutoExport(output: OutputStream, uri: String) {
        prefsManager.autoExportUri = uri
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = jsonManager.exportJsonData()
            try {
                output.use {
                    output.write(jsonData.toByteArray())
                }
                showMessage(R.string.export_success)

                val now = System.currentTimeMillis()
                prefsManager.lastAutoExportTime = now
                _lastAutoExport.postValue(now)
            } catch (e: Exception) {
                showMessage(R.string.export_fail)
            }
        }
    }

    fun disableAutoExport() {
        prefsManager.lastAutoExportTime = 0
        prefsManager.autoExportFailed = false
        _releasePersistableUriEvent.send(prefsManager.autoExportUri)
        prefsManager.autoExportUri = ""
    }

    fun importData(input: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = try {
                input.reader().readText()
            } catch (e: Exception) {
                showMessage(R.string.import_bad_input)
                return@launch
            }
            val result = jsonManager.importJsonData(jsonData)
            showMessage(when (result) {
                ImportResult.BAD_FORMAT -> R.string.import_bad_format
                ImportResult.BAD_DATA -> R.string.import_bad_data
                ImportResult.FUTURE_VERSION -> R.string.import_future_version
                ImportResult.SUCCESS -> R.string.import_success
            })
        }
    }

    fun clearData() {
        viewModelScope.launch {
            notesRepository.clearAllData()
            labelsRepository.clearAllData()
            showMessage(R.string.pref_data_clear_success_message)
        }
    }

    private fun showMessage(messageId: Int) {
        _messageEvent.postValue(Event(messageId))
    }
}
