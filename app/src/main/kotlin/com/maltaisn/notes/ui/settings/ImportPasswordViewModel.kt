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

class ImportPasswordViewModel @AssistedInject constructor(
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _setDialogDataEvent = MutableLiveData<Event<String>>()
    val setDialogDataEvent: LiveData<Event<String>>
        get() = _setDialogDataEvent

    private var password = savedStateHandle[KEY_PASSWORD] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_PASSWORD] = value
        }

    fun onPasswordChanged(password: String) {
        this.password = password
    }

    fun start() {
        if (KEY_PASSWORD in savedStateHandle) {
            _setDialogDataEvent.send(this.password)
        }
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<ImportPasswordViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): ImportPasswordViewModel
    }

    companion object {
        private const val KEY_PASSWORD = "password"
    }
}