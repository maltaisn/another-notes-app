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

package com.maltaisn.notes.ui.sync.passwordreset

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.maltaisn.notes.R
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.launch
import javax.inject.Inject


class PasswordResetViewModel @Inject constructor(
        private val loginRepository: LoginRepository
) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _emailError = MutableLiveData<Int?>()
    val emailError: LiveData<Int?>
        get() = _emailError

    private val _resetBtnEnabled = MutableLiveData<Boolean>()
    val resetBtnEnabled: LiveData<Boolean>
        get() = _resetBtnEnabled

    private val _dismissEvent = MutableLiveData<Event<Unit>>()
    val dismissEvent: LiveData<Event<Unit>>
        get() = _dismissEvent

    // No need to save this in saved state handle, EditText saves it
    // and [onEmailEntered] method is called when dialog is recreated.
    private var email = ""


    fun onEmailEntered(email: String) {
        this.email = email

        val isNotBlank = email.isNotBlank()

        _resetBtnEnabled.value = isNotBlank
        if (emailError.value != null && isNotBlank) {
            // Clear invalid email error
            _emailError.value = null
        }
    }

    fun resetPassword() {
        viewModelScope.launch {
            try {
                loginRepository.sendPasswordResetEmail(email)
                _messageEvent.send(R.string.sync_password_reset_success_message)
                _dismissEvent.send()

            } catch (e: FirebaseException) {
                when (e) {
                    is FirebaseAuthInvalidCredentialsException,
                    is FirebaseAuthInvalidUserException -> {
                        // Email is invalid
                        _emailError.value = R.string.sync_email_invalid_error
                        _resetBtnEnabled.value = false
                    }
                    else -> {
                        // No internet connection or unknown error.
                        _messageEvent.send(R.string.sync_failed_message)
                        _dismissEvent.send()
                    }
                }
            }
        }
    }

}
