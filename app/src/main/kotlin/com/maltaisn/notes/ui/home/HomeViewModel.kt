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

package com.maltaisn.notes.ui.home

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.DebugUtils
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.R
import com.maltaisn.notes.model.LoginRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject


class HomeViewModel @Inject constructor(
        notesRepository: NotesRepository,
        val loginRepository: LoginRepository,
        prefs: SharedPreferences
) : NoteViewModel(notesRepository, prefs), NoteAdapter.Callback {

    private var noteListJob: Job? = null

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _stopRefreshEvent = MutableLiveData<Event<Unit>>()
    val stopRefreshEvent: LiveData<Event<Unit>>
        get() = _stopRefreshEvent


    init {
        setNoteStatus(NoteStatus.ACTIVE)
    }

    fun setNoteStatus(status: NoteStatus) {
        _noteStatus.value = status

        // Cancel previous flow collection
        noteListJob?.cancel()

        // Update note items live data when database flow emits a list.
        noteListJob = viewModelScope.launch {
            notesRepository.getNotesByStatus(status).collect { notes ->
                createListItems(status, notes)
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

    fun syncNotes() {
        viewModelScope.launch {
            if (loginRepository.isUserSignedIn) {
                // Sync notes if last sync was beyond delay for manual syncing.
                val lastSync = prefs.getLong(PreferenceHelper.LAST_SYNC_TIME, 0)
                val nextSyncTime = lastSync + PreferenceHelper.MIN_MANUAL_SYNC_INTERVAL.toLongMilliseconds()
                if (System.currentTimeMillis() >= nextSyncTime) {
                    try {
                        notesRepository.syncNotes()
                    } catch (e: IOException) {
                        // Sync failed for unknown reason.
                        Log.e(TAG, "Couldn't sync notes", e)
                        _messageEvent.send(R.string.sync_failed_message)
                    }
                }
            }
            _stopRefreshEvent.send()
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

    override val selectedNoteStatus: NoteStatus?
        get() = noteStatus.value

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        // Update last remind time when user dismisses message.
        prefs.edit().putLong(PreferenceHelper.LAST_TRASH_REMIND_TIME,
                System.currentTimeMillis()).apply()

        // Remove message item in list
        changeListItems { it.removeAt(pos) }
    }

    override val isNoteSwipeEnabled: Boolean
        get() = noteStatus.value == NoteStatus.ACTIVE && selectedNotes.isEmpty()

    override fun onNoteSwiped(pos: Int) {
        // Archive note
        val note = (noteItems.value!![pos] as NoteItem).note
        changeNotesStatus(setOf(note), NoteStatus.ARCHIVED)
    }

    private fun createListItems(status: NoteStatus, notes: List<Note>) {
        listItems = buildList {
            if (status == NoteStatus.TRASHED && notes.isNotEmpty()) {
                // If needed, add reminder that notes get auto-deleted when in trash.
                val lastReminder = prefs.getLong(PreferenceHelper.LAST_TRASH_REMIND_TIME, 0)
                if (System.currentTimeMillis() - lastReminder >
                        PreferenceHelper.TRASH_REMINDER_DELAY.toLongMilliseconds()) {
                    this += MessageItem(TRASH_REMINDER_ITEM_ID,
                            R.string.message_trash_reminder,
                            PreferenceHelper.TRASH_AUTO_DELETE_DELAY.inDays.toInt())
                }
            }

            // Add note items
            for (note in notes) {
                val checked = selectedNotes.any { it.id == note.id }
                this += NoteItem(note.id, note, checked, emptyList(), emptyList())
            }
        }
    }

    companion object {
        private const val TRASH_REMINDER_ITEM_ID = -1L

        private val TAG = HomeViewModel::class.java.simpleName
    }

}
