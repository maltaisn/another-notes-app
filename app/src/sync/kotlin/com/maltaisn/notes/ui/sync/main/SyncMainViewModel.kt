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

package com.maltaisn.notes.ui.sync.main

import androidx.lifecycle.*
import com.google.firebase.FirebaseException
import com.maltaisn.notes.R
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import com.maltaisn.notes.ui.sync.SyncPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


class SyncMainViewModel @Inject constructor(
        private val loginRepository: LoginRepository
) : ViewModel() {

    private val _changePageEvent = MutableLiveData<Event<SyncPage>>()
    val changePageEvent: LiveData<Event<SyncPage>>
        get() = _changePageEvent

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    val currentUser = loginRepository.authStateChannel.asFlow()
            .map { loginRepository.currentUser }
            .asLiveData(viewModelScope.coroutineContext)


    fun goToPage(page: SyncPage) {
        _changePageEvent.send(page)
    }

    fun signOut() {
        loginRepository.signOut()
        _messageEvent.send(R.string.sync_sign_out_success_message)
    }

    fun resendVerification() {
        viewModelScope.launch {
            try {
                // Check if user has verified their email first. If not send verification.
                loginRepository.reloadUser()
                if (!loginRepository.isUserEmailVerified) {
                    withContext(Dispatchers.IO) {
                        loginRepository.sendVerificationEmail()
                    }
                    _messageEvent.send(R.string.sync_verification_success_message)
                }
            } catch (e: FirebaseException) {
                // Network error, too many requests, or other unknown error.
                _messageEvent.send(R.string.sync_failed_message)
            }
        }
    }

    fun checkVerification() {
        viewModelScope.launch {
            try {
                loginRepository.reloadUser()
            } catch (e: FirebaseException) {
                // Network error, too many requests, or other unknown error.
            }
        }
    }

}