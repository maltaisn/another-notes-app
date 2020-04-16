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


package com.maltaisn.notes.model

import android.content.SharedPreferences
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class LoginRepository @Inject constructor(
        private val fbAuth: FirebaseAuth,
        private val prefs: SharedPreferences,
        private val notesDao: NotesDao) {

    val currentUser: FirebaseUser?
        get() = fbAuth.currentUser

    open val isUserSignedIn: Boolean
        get() = currentUser != null


    fun addAuthStateListener(onAuth: () -> Unit) {
        fbAuth.addAuthStateListener { onAuth() }
    }

    suspend fun signIn(email: String, password: String) {
        fbAuth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUp(email: String, password: String) {
        fbAuth.createUserWithEmailAndPassword(email, password).await()
    }

    suspend fun sendVerificationEmail() {
        currentUser?.sendEmailVerification()?.await()
    }

    suspend fun sendPasswordResetEmail(email: String) {
        fbAuth.sendPasswordResetEmail(email).await()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        reauthenticateUser(currentPassword)
        currentUser?.updatePassword(newPassword)
    }

    suspend fun deleteUser(password: String) {
        reauthenticateUser(password)
        currentUser?.delete()?.await()
    }

    suspend fun reloadUser() {
        currentUser?.reload()?.await()
    }

    suspend fun reauthenticateUser(password: String) {
        val user = currentUser ?: return
        user.reauthenticate(EmailAuthProvider.getCredential(user.email!!, password)).await()
    }

    fun signOut() {
        fbAuth.signOut()
    }

    companion object {
        const val PASSWORD_MIN_LENGTH = 6
        const val PASSWORD_MAX_LENGTH = 32

        val PASSWORD_RANGE = PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
    }

}
