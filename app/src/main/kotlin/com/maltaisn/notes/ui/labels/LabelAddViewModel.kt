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
import kotlinx.coroutines.launch
import javax.inject.Inject

class LabelAddViewModel @Inject constructor(
    private val labelsRepository: LabelsRepository,
) : ViewModel() {

    private val _nameError = MutableLiveData(true)
    val nameError: LiveData<Boolean>
        get() = _nameError

    private var labelName = ""

    fun onNameChanged(name: String) {
        labelName = name
        viewModelScope.launch {
            // Label name must not be empty and must not exist.
            _nameError.value = (name.isEmpty() || labelsRepository.getLabelByName(name) != null)
        }
    }

    fun addLabel() {
        viewModelScope.launch {
            labelsRepository.insertLabel(Label(Label.NO_ID, labelName))
        }
    }
}
