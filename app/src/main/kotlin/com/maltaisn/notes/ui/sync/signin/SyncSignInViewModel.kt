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

package com.maltaisn.notes.ui.sync.signin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.sync.SyncPage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


class SyncSignInViewModel @Inject constructor(private val fbAuth: FirebaseAuth) : ViewModel() {

    private val _changePageEvent = MutableLiveData<Event<SyncPage>>()
    val changePageEvent: LiveData<Event<SyncPage>>
        get() = _changePageEvent

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _clearFieldsEvent = MutableLiveData<Event<Unit>>()
    val clearFieldsEvent: LiveData<Event<Unit>>
        get() = _clearFieldsEvent

    private val _fieldError = MutableLiveData<FieldError?>()
    val fieldError: LiveData<FieldError?>
        get() = _fieldError

    private var email: CharSequence = ""
    private var password: CharSequence = ""

    fun goToPage(page: SyncPage) {
        _changePageEvent.value = Event(page)
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
        if (fieldError.value === noPasswordError && password.isNotEmpty()) {
            // User entered password or password of valid length, clear error.
            _fieldError.value = null
        }
    }

    fun signIn() {
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

        viewModelScope.launch {
            try {
                // Log in to account
                fbAuth.signInWithEmailAndPassword(
                        email.toString(), password.toString()).await()

                _messageEvent.value = Event(R.string.sync_sign_in_success_message)
                _fieldError.value = null
                _clearFieldsEvent.value = Event(Unit)

                goToPage(SyncPage.MAIN)

            } catch (e: FirebaseException) {
                _messageEvent.value = Event(when (e) {
                    is FirebaseAuthException -> {
                        // Invalid email or password.
                        R.string.sync_sign_in_failed_wrong_message
                    }
                    is FirebaseTooManyRequestsException -> {
                        // Too many attempts to sign in.
                        R.string.sync_sign_in_failed_attempts_message
                    }
                    else -> {
                        // No internet connection or other error.
                        R.string.sync_sign_in_failed_unknown_message
                    }
                })
            }
        }
    }

    class FieldError(val location: Location, val messageId: Int) {
        enum class Location {
            EMAIL,
            PASSWORD
        }
    }

    companion object {
        private val noEmailError = FieldError(FieldError.Location.EMAIL,
                R.string.sync_field_missing_error)

        private val noPasswordError = FieldError(FieldError.Location.PASSWORD,
                R.string.sync_field_missing_error)
    }

}
