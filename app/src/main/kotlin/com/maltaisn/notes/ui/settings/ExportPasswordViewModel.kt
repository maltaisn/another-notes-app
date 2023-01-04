package com.maltaisn.notes.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class ExportPasswordViewModel @AssistedInject constructor(
    @Assisted private val savedStateHandle: SavedStateHandle,
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

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<ExportPasswordViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): ExportPasswordViewModel
    }

    companion object {
        private const val KEY_PASSWORD = "password"
        private const val KEY_PASSWORD_REPEAT = "passwordRepeat"
    }
}