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

package com.maltaisn.notes.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.launch
import javax.inject.Inject

class LabelEditViewModel @Inject constructor(
    private val labelsRepository: LabelsRepository,
) : ViewModel() {

    private val _changeNameEvent = MutableLiveData<Event<String>>()
    val changeNameEvent: LiveData<Event<String>>
        get() = _changeNameEvent

    private val _nameError = MutableLiveData(true)
    val nameError: LiveData<Boolean>
        get() = _nameError

    private var label = NO_LABEL
    private var labelName = ""

    fun start(labelId: Long) {
        viewModelScope.launch {
            val label = labelsRepository.getLabelById(labelId)
            if (label != null) {
                // Edit label, set name initially
                this@LabelEditViewModel.label = label
                _changeNameEvent.send(label.name)
            }
        }
    }

    fun onNameChanged(name: String) {
        labelName = name.trim().replace("""\s+""".toRegex(), " ")
        viewModelScope.launch {
            // Label name must not be empty and must not exist.
            // Ignore name clash if label is the one being edited.
            val existingLabel = labelsRepository.getLabelByName(name)
            _nameError.value = (name.isEmpty() ||
                    existingLabel != null && existingLabel.id != label.id)
        }
    }

    fun addLabel() {
        viewModelScope.launch {
            if (label == NO_LABEL) {
                labelsRepository.insertLabel(Label(Label.NO_ID, labelName))
            } else {
                labelsRepository.updateLabel(label.copy(name = labelName))
            }
        }
    }

    companion object {
        private val NO_LABEL = Label(Label.NO_ID, "_")
    }

}
