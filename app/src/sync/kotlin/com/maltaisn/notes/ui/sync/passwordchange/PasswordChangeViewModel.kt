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

package com.maltaisn.notes.ui.sync.passwordchange

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.maltaisn.notes.R
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import com.maltaisn.notes.ui.sync.passwordchange.PasswordChangeViewModel.FieldError.Location
import kotlinx.coroutines.launch
import javax.inject.Inject


class PasswordChangeViewModel @Inject constructor(
        private val loginRepository: LoginRepository) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _fieldError = MutableLiveData<FieldError?>()
    val fieldError: LiveData<FieldError?>
        get() = _fieldError

    private val _passwordConfirmEnabled = MutableLiveData<Boolean>(true)
    val passwordConfirmEnabled: LiveData<Boolean>
        get() = _passwordConfirmEnabled

    private val _dismissEvent = MutableLiveData<Event<Unit>>()
    val dismissEvent: LiveData<Event<Unit>>
        get() = _dismissEvent


    private var passwordCurrent = ""
    private var passwordNew = ""
    private var passwordConfirm = ""


    fun setEnteredCurrentPassword(password: String) {
        passwordCurrent = password
        if (password.isNotEmpty() && (fieldError.value === noCurrentPasswordError
                || fieldError.value === wrongCurrentPasswordError)) {
            // User entered email, clear error.
            _fieldError.value = null
        }
    }

    fun setEnteredNewPassword(password: String) {
        passwordNew = password
        if (fieldError.value === passwordLengthError
                && password.length in LoginRepository.PASSWORD_RANGE
                || fieldError.value === noNewPasswordError && password.isNotEmpty()) {
            // User entered password or password of valid length, clear error.
            _fieldError.value = null
        }
    }

    fun setEnteredPasswordConfirm(password: String) {
        passwordConfirm = password
    }

    fun setPasswordConfirmEnabled(enabled: Boolean) {
        _passwordConfirmEnabled.value = enabled
    }

    fun changePassword() {
        if (passwordCurrent.isBlank()) {
            // Missing current password
            _fieldError.value = noCurrentPasswordError
            return
        }
        if (passwordNew.isEmpty()) {
            // Missing new password
            _fieldError.value = noNewPasswordError
            return
        }

        if (passwordNew.length !in LoginRepository.PASSWORD_RANGE) {
            // Password has invalid length.
            _fieldError.value = passwordLengthError
            return
        }

        if (passwordNew != passwordConfirm && passwordConfirmEnabled.value == true) {
            // Passwords don't match.
            _fieldError.value = FieldError(Location.PASSWORD_CONFIRM,
                    R.string.sync_password_mismatch_error)
            return
        }

        viewModelScope.launch {
            try {
                // Change password
                loginRepository.changePassword(passwordCurrent, passwordNew)

                _messageEvent.send(R.string.sync_password_change_success_message)
                _dismissEvent.send()

            } catch (e: FirebaseException) {
                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        // Invalid current password
                        _fieldError.value = wrongCurrentPasswordError
                    }
                    else -> {
                        // No internet connection, too many requests, invalid user, or unknown error.
                        _messageEvent.send(R.string.sync_failed_message)
                        _dismissEvent.send()
                    }
                }
            }
        }
    }

    class FieldError(val location: Location, val messageId: Int, vararg val args: Any) {
        enum class Location {
            PASSWORD_CURRENT,
            PASSWORD_NEW,
            PASSWORD_CONFIRM
        }
    }

    companion object {
        private val wrongCurrentPasswordError = FieldError(Location.PASSWORD_CURRENT,
                R.string.sync_password_wrong_error)

        private val noCurrentPasswordError = FieldError(Location.PASSWORD_CURRENT,
                R.string.sync_field_missing_error)

        private val passwordLengthError = FieldError(Location.PASSWORD_NEW,
                R.string.sync_password_length_error,
                LoginRepository.PASSWORD_MIN_LENGTH, LoginRepository.PASSWORD_MAX_LENGTH)

        private val noNewPasswordError = FieldError(Location.PASSWORD_NEW,
                R.string.sync_field_missing_error)
    }

}
