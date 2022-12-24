/*
 * Copyright 2022 Nicolas Maltais
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

package com.maltaisn.notes.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch

class LabelEditViewModel @AssistedInject constructor(
    private val labelsRepository: LabelsRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _setLabelEvent = MutableLiveData<Event<Label>>()
    val setLabelEvent: LiveData<Event<Label>>
        get() = _setLabelEvent

    private val _labelAddEvent = MutableLiveData<Event<Label>>()
    val labelAddEvent: LiveData<Event<Label>>
        get() = _labelAddEvent

    private val _labelError = MutableLiveData(Error.NONE)
    val nameError: LiveData<Error>
        get() = _labelError

    private var labelId = Label.NO_ID

    private var labelName = savedStateHandle[KEY_NAME] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_NAME] = value
        }

    private var hidden = savedStateHandle[KEY_HIDDEN] ?: false
        set(value) {
            field = value
            savedStateHandle[KEY_HIDDEN] = value
        }

    fun start(labelId: Long) {
        this.labelId = labelId
        if (KEY_NAME in savedStateHandle) {
            _setLabelEvent.send(Label(labelId, labelName, hidden))
            updateError()
        } else {
            viewModelScope.launch {
                val label = labelsRepository.getLabelById(labelId)
                if (label != null) {
                    // Edit label, set name initially
                    labelName = label.name
                    hidden = label.hidden
                    _setLabelEvent.send(label)
                } else {
                    labelName = ""
                    hidden = false
                    _setLabelEvent.send(Label(Label.NO_ID, "", false))
                }
                updateError()
            }
        }
    }

    fun onNameChanged(name: String) {
        labelName = name.trim().replace("""\s+""".toRegex(), " ")
        updateError()
    }

    fun onHiddenChanged(hidden: Boolean) {
        this.hidden = hidden
    }

    fun addLabel() {
        var label = Label(labelId, labelName, hidden)
        viewModelScope.launch {
            if (labelId == Label.NO_ID) {
                val id = labelsRepository.insertLabel(label)
                label = label.copy(id = id)
            } else {
                // Must use update, using insert will remove the label references despite being update on conflict.
                labelsRepository.updateLabel(label)
            }
            _labelAddEvent.send(label)
        }
    }

    private fun updateError() {
        viewModelScope.launch {
            // Label name must not be empty and must not exist.
            // Ignore name clash if label is the one being edited.
            _labelError.value = if (labelName.isEmpty()) {
                Error.BLANK
            } else {
                val existingLabel = labelsRepository.getLabelByName(labelName)
                if (existingLabel != null && existingLabel.id != labelId) {
                    Error.DUPLICATE
                } else {
                    Error.NONE
                }
            }
        }
    }

    enum class Error {
        NONE,
        DUPLICATE,
        BLANK
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<LabelEditViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): LabelEditViewModel
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_HIDDEN = "hidden"
    }
}
