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

package com.maltaisn.notes.ui.main

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.DebugUtils
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.main.adapter.MessageItem
import com.maltaisn.notes.ui.main.adapter.NoteItem
import com.maltaisn.notes.ui.main.adapter.NoteListItem
import com.maltaisn.notes.ui.main.adapter.NoteListLayoutMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject


class MainViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val prefs: SharedPreferences) : ViewModel() {


    private var noteListJob: Job? = null

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _noteItems = MutableLiveData<List<NoteListItem>>()
    val noteItems : LiveData<List<NoteListItem>>
        get() = _noteItems

    private val _listLayoutMode = MutableLiveData<NoteListLayoutMode>()
    val listLayoutMode : LiveData<NoteListLayoutMode>
        get() = _listLayoutMode

    private val _itemClickEvent = MutableLiveData<Event<NoteItem>>()
    val itemClickEvent: LiveData<Event<NoteItem>>
        get() = _itemClickEvent


    init {
        setNoteStatus(NoteStatus.ACTIVE)

        val layoutModeVal = prefs.getInt(PreferenceHelper.LIST_LAYOUT_MODE,
                NoteListLayoutMode.LIST.value)
        _listLayoutMode.value = NoteListLayoutMode.values().find { it.value == layoutModeVal }
    }


    fun setNoteStatus(status: NoteStatus) {
        _noteStatus.value = status

        // Cancel previous flow collection
        noteListJob?.cancel()

        // Update note items live data when database flow emits a list.
        noteListJob = viewModelScope.launch {
            notesRepository.getNotesByStatus(status).collect { notes ->
                _noteItems.value = buildItemListFromNotes(status, notes)
            }
        }
    }

    fun toggleListLayoutMode() {
        val mode = when (_listLayoutMode.value!!) {
            NoteListLayoutMode.LIST -> NoteListLayoutMode.GRID
            NoteListLayoutMode.GRID -> NoteListLayoutMode.LIST
        }
        _listLayoutMode.value = mode
        prefs.edit().putInt(PreferenceHelper.LIST_LAYOUT_MODE, mode.value).apply()
    }

    fun emptyTrash() {
        viewModelScope.launch {
            notesRepository.emptyTrash()
        }
    }

    fun addDebugNotes() {
        viewModelScope.launch {
            val status = _noteStatus.value!!
            repeat(3) {
                notesRepository.insertNote(DebugUtils.getRandomNote(status))
            }
        }
    }

    private fun buildItemListFromNotes(status: NoteStatus, notes: List<Note>): List<NoteListItem> = buildList {
        if (status == NoteStatus.TRASHED && notes.isNotEmpty()) {
            // If needed, add reminder that notes get auto-deleted when in trash.
            val lastReminder = prefs.getLong(PreferenceHelper.LAST_TRASH_REMIND_TIME, 0)
            if (System.currentTimeMillis() - lastReminder > TRASH_REMINDER_DELAY * 86400000L) {
                this += MessageItem(TRASH_REMINDER_ITEM_ID,
                        R.string.message_trash_reminder,
                        PreferenceHelper.TRASH_AUTO_DELETE_DELAY) {
                    // Update last remind time when user dismisses message.
                    prefs.edit().putLong(PreferenceHelper.LAST_TRASH_REMIND_TIME,
                            System.currentTimeMillis()).apply()
                    setNoteStatus(status)  // Kinda inefficient
                }
            }
        }

        // Add note items
        val onNoteClick = { item: NoteItem ->
            _itemClickEvent.value = Event(item)
        }
        for (note in notes) {
            this += NoteItem(note.id, note, onNoteClick)
        }
    }

    companion object {
        private const val TRASH_REMINDER_DELAY = 60
        private const val TRASH_REMINDER_ITEM_ID = -1L
    }

}
