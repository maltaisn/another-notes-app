/*
 * Copyright 2023 Nicolas Maltais
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

import android.os.Build
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.R
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.JsonManager.ImportResult
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SettingsViewModel @AssistedInject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefsManager: PrefsManager,
    private val jsonManager: JsonManager,
    private val reminderAlarmManager: ReminderAlarmManager,
    @Assisted private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _lastAutoExport = MutableLiveData<Long>()
    val lastAutoExport: LiveData<Long>
        get() = _lastAutoExport

    private val _releasePersistableUriEvent = MutableLiveData<Event<String>>()
    val releasePersistableUriEvent: LiveData<Event<String>>
        get() = _releasePersistableUriEvent

    private val _showImportPasswordDialogEvent = MutableLiveData<Event<Unit>>()
    val showImportPasswordDialogEvent: LiveData<Event<Unit>>
        get() = _showImportPasswordDialogEvent

    private val _askNotificationPermission = MutableLiveData<Event<Unit>>()
    val askNotificationPermission: LiveData<Event<Unit>>
        get() = _askNotificationPermission

    private val _askReminderPermission = MutableLiveData<Event<Unit>>()
    val askReminderPermission: LiveData<Event<Unit>>
        get() = _askReminderPermission

    private var importJsonData = savedStateHandle[KEY_IMPORTED_JSON_DATA] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_IMPORTED_JSON_DATA] = value
        }

    init {
        _lastAutoExport.value = prefsManager.lastAutoExportTime
    }

    fun exportData(output: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = try {
                jsonManager.exportJsonData()
            } catch (e: Exception) {
                showMessage(R.string.export_serialization_fail)
                return@launch
            }

            try {
                output.use {
                    // bufferedWriter().write fails here for some reason...
                    output.write(jsonData.toByteArray())
                }
                showMessage(R.string.export_success)
            } catch (e: Exception) {
                showMessage(R.string.export_fail)
            }
        }
    }

    fun setupAutoExport(output: OutputStream, uri: String) {
        prefsManager.autoExportUri = uri
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = try {
                jsonManager.exportJsonData()
            } catch (e: Exception) {
                showMessage(R.string.export_serialization_fail)
                return@launch
            }

            try {
                output.use {
                    output.write(jsonData.toByteArray())
                }
                showMessage(R.string.export_success)

                val now = System.currentTimeMillis()
                prefsManager.lastAutoExportTime = now
                _lastAutoExport.postValue(now)
            } catch (e: Exception) {
                showMessage(R.string.export_fail)
            }
        }
    }

    fun disableAutoExport() {
        prefsManager.disableAutoExport()
        _releasePersistableUriEvent.send(prefsManager.autoExportUri)
    }

    fun importData(input: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = try {
                input.bufferedReader().readText()
            } catch (e: Exception) {
                showMessage(R.string.import_bad_input)
                return@launch
            }
            val result = jsonManager.importJsonData(jsonData)
            if (result == ImportResult.KEY_MISSING_OR_INCORRECT && Build.VERSION.SDK_INT >= 26) {
                // Show dialog for user to enter encryption password
                _showImportPasswordDialogEvent.postValue(Event(Unit))
                importJsonData = jsonData
            } else {
                // Show result in case the imported file was not encrypted
                showImportResultMessage(result)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun importSavedEncryptedJsonData(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val salt = Base64.decode(prefsManager.encryptedImportKeyDerivationSalt, BASE64_FLAGS)
            val decryptionKey = deriveKey(password, salt)

            val result = jsonManager.importJsonData(importJsonData, decryptionKey)
            importJsonData = ""
            showImportResultMessage(result)
        }
    }

    private fun showImportResultMessage(result: ImportResult) {
        showMessage(when (result) {
            ImportResult.BAD_FORMAT -> R.string.import_bad_format
            ImportResult.BAD_DATA -> R.string.import_bad_data
            ImportResult.FUTURE_VERSION -> R.string.import_future_version
            ImportResult.KEY_MISSING_OR_INCORRECT -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    R.string.encrypted_import_key_error
                } else {
                    R.string.encrypted_import_encryption_unsupported
                }
            }

            ImportResult.SUCCESS -> R.string.import_success
        })

        viewModelScope.launch {
            if ((result == ImportResult.SUCCESS || result == ImportResult.FUTURE_VERSION) &&
                notesRepository.getNotesWithReminder().firstOrNull() != null
            ) {
                _askNotificationPermission.send()
                _askReminderPermission.send()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun generateExportKeyFromPassword(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Generate salt
            val secureRandom = SecureRandom()
            val salt = ByteArray(128)
            secureRandom.nextBytes(salt)
            // Save salt for later use
            prefsManager.encryptedExportKeyDerivationSalt = Base64.encodeToString(salt, BASE64_FLAGS)

            // Derive key
            val aesKey = deriveKey(password, salt)

            // Store key in Android KeyStore
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.setEntry(
                EXPORT_ENCRYPTION_KEY_ALIAS,
                KeyStore.SecretKeyEntry(aesKey),
                KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            showMessage(R.string.encrypted_export_key_generation_successful)
        }
    }

    fun deleteExportKey() {
        viewModelScope.launch(Dispatchers.IO) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(EXPORT_ENCRYPTION_KEY_ALIAS)
            showMessage(R.string.encrypted_export_key_deletion_successful)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val secretKey = secretKeyFactory.generateSecret(keySpec)

        return SecretKeySpec(secretKey.encoded, KeyProperties.KEY_ALGORITHM_AES)
    }

    fun clearData() {
        viewModelScope.launch {
            notesRepository.clearAllData()
            labelsRepository.clearAllData()
            reminderAlarmManager.removeAllAlarms()
            showMessage(R.string.pref_data_clear_success_message)
        }
    }

    private fun showMessage(messageId: Int) {
        _messageEvent.postValue(Event(messageId))
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<SettingsViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): SettingsViewModel
    }

    companion object {
        private const val KEY_IMPORTED_JSON_DATA = "importedJsonData"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING
        private const val EXPORT_ENCRYPTION_KEY_ALIAS = "export_key"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2withHmacSHA512"
        private const val PBKDF2_ITERATIONS = 120000
        private const val PBKDF2_KEY_LENGTH = 256
    }
}
