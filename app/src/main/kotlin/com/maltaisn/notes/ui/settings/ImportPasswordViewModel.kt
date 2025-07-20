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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ImportPasswordViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _setDialogDataEvent = MutableLiveData<Event<String>>()
    val setDialogDataEvent: LiveData<Event<String>>
        get() = _setDialogDataEvent

    private var password = savedStateHandle[KEY_PASSWORD] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_PASSWORD] = value
        }

    fun onPasswordChanged(password: String) {
        this.password = password
    }

    fun start() {
        if (KEY_PASSWORD in savedStateHandle) {
            _setDialogDataEvent.send(this.password)
        }
    }

    companion object {
        private const val KEY_PASSWORD = "password"
    }
}