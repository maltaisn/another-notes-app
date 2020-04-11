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

package com.maltaisn.notes.ui.sync.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.maltaisn.notes.R
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import com.maltaisn.notes.ui.sync.SyncPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


class SyncSignUpViewModel @Inject constructor(
        private val loginRepository: LoginRepository) : ViewModel() {

    private val _changePageEvent = MutableLiveData<Event<SyncPage>>()
    val changePageEvent: LiveData<Event<SyncPage>>
        get() = _changePageEvent

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _fieldError = MutableLiveData<FieldError?>()
    val fieldError: LiveData<FieldError?>
        get() = _fieldError

    private val _passwordConfirmEnabled = MutableLiveData<Boolean>(true)
    val passwordConfirmEnabled: LiveData<Boolean>
        get() = _passwordConfirmEnabled

    private val _clearFieldsEvent = MutableLiveData<Event<Unit>>()
    val clearFieldsEvent: LiveData<Event<Unit>>
        get() = _clearFieldsEvent

    private var email: CharSequence = ""
    private var password: CharSequence = ""
    private var passwordConfirm: CharSequence = ""


    fun goToPage(page: SyncPage) {
        _changePageEvent.send(page)
    }

    fun setEnteredEmail(email: CharSequence) {
        this.email = email
        if (fieldError.value === noEmailError && email.isNotBlank()) {
            // User entered email, clear error.
            _fieldError.value = null
        }
    }

    fun setEnteredPassword(password: CharSequence) {
        this.password = password
        if (fieldError.value === passwordLengthError
                && password.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
                || fieldError.value === noPasswordError && password.isNotEmpty()) {
            // User entered password or password of valid length, clear error.
            _fieldError.value = null
        }
    }

    fun setEnteredPasswordConfirm(password: CharSequence) {
        passwordConfirm = password
    }

    fun setPasswordConfirmEnabled(enabled: Boolean) {
        _passwordConfirmEnabled.value = enabled
    }

    fun signUp() {
        if (email.isBlank()) {
            // Missing email
            _fieldError.value = noEmailError
            return
        }
        if (password.isEmpty()) {
            // Missing password
            _fieldError.value = noPasswordError
            return
        }

        if (password.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            // Password has invalid length.
            _fieldError.value = passwordLengthError
            return
        }

        if (password != passwordConfirm && passwordConfirmEnabled.value == true) {
            // Passwords don't match.
            _fieldError.value = FieldError(FieldError.Location.PASSWORD_CONFIRM,
                    R.string.sync_sign_up_password_mismatch_error)
            return
        }

        viewModelScope.launch {
            try {
                // Create new account and send verification email.
                withContext(Dispatchers.IO) {

                    loginRepository.signUp(email.toString(), password.toString())
                    loginRepository.sendVerificationEmail()
                }

                _messageEvent.send(R.string.sync_sign_up_success_message)
                _fieldError.value = null
                _clearFieldsEvent.send()

                goToPage(SyncPage.MAIN)

            } catch (e: FirebaseException) {
                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        // Invalid email address.
                        _fieldError.value = FieldError(FieldError.Location.EMAIL,
                                R.string.sync_sign_up_email_invalid_error)
                    }
                    is FirebaseAuthUserCollisionException -> {
                        // Email is already in use.
                        _messageEvent.send(R.string.sync_sign_up_failed_email_used_message)
                    }
                    else -> {
                        // No internet connection, too many requests, or other error.
                        _messageEvent.send(R.string.sync_sign_in_failed_unknown_message)
                    }
                }
            }
        }
    }

    class FieldError(val location: Location, val messageId: Int, vararg val args: Any) {
        enum class Location {
            EMAIL,
            PASSWORD,
            PASSWORD_CONFIRM
        }
    }

    companion object {
        private const val PASSWORD_MIN_LENGTH = 6
        private const val PASSWORD_MAX_LENGTH = 32

        private val passwordLengthError = FieldError(FieldError.Location.PASSWORD,
                R.string.sync_sign_up_password_length_error,
                PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH)

        private val noEmailError = FieldError(FieldError.Location.EMAIL,
                R.string.sync_field_missing_error)

        private val noPasswordError = FieldError(FieldError.Location.PASSWORD,
                R.string.sync_field_missing_error)
    }

}
