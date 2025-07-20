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
class ExportPasswordViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _setDialogDataEvent = MutableLiveData<Event<Pair<String, String>>>()
    val setDialogDataEvent: LiveData<Event<Pair<String, String>>>
        get() = _setDialogDataEvent

    private val _passwordValid = MutableLiveData(false)
    val passwordValid: LiveData<Boolean>
        get() = _passwordValid

    private val _passwordRepeatErrorShown = MutableLiveData(false)
    val passwordRepeatErrorShown: LiveData<Boolean>
        get() = _passwordRepeatErrorShown

    private var password = savedStateHandle[KEY_PASSWORD] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_PASSWORD] = value
        }

    private var passwordRepeat = savedStateHandle[KEY_PASSWORD_REPEAT] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_PASSWORD_REPEAT] = value
        }

    fun start() {
        if (KEY_PASSWORD in savedStateHandle || KEY_PASSWORD_REPEAT in savedStateHandle) {
            _setDialogDataEvent.send(Pair(this.password, this.passwordRepeat))
        }
    }

    fun onPasswordChanged(password: String, passwordRepeat: String) {
        // Check if passwords match. Also don't allow empty passwords
        _passwordValid.value = (password == passwordRepeat) && password.isNotEmpty()
        _passwordRepeatErrorShown.value = password != passwordRepeat
        this.password = password
        this.passwordRepeat = passwordRepeat
    }

    companion object {
        private const val KEY_PASSWORD = "password"
        private const val KEY_PASSWORD_REPEAT = "passwordRepeat"
    }
}