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

package com.maltaisn.notes.ui.sync.accountdelete

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
import kotlinx.coroutines.launch
import javax.inject.Inject


class AccountDeleteViewModel @Inject constructor(
        private val loginRepository: LoginRepository) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _passwordError = MutableLiveData<Int?>()
    val passwordError: LiveData<Int?>
        get() = _passwordError

    private val _deleteBtnEnabled = MutableLiveData<Boolean>(false)
    val deleteBtnEnabled: LiveData<Boolean>
        get() = _deleteBtnEnabled

    private val _dismissEvent = MutableLiveData<Event<Unit>>()
    val dismissEvent: LiveData<Event<Unit>>
        get() = _dismissEvent

    private var password = ""


    fun onPasswordEntered(password: String) {
        this.password = password

        val isNotEmpty = password.isNotEmpty()

        _deleteBtnEnabled.value = isNotEmpty
        if (passwordError.value != null && isNotEmpty) {
            // Clear wrong password error.
            _passwordError.value = null
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                loginRepository.deleteUser(password)
                _messageEvent.send(R.string.sync_account_delete_success_message)
                _dismissEvent.send()

            } catch (e: FirebaseException) {
                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        // Password is wrong.
                        _passwordError.value = R.string.sync_password_wrong_error
                        _deleteBtnEnabled.value = false
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
