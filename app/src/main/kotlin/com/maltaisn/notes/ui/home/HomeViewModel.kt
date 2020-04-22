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

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.DebugUtils
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.SyncManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.PlaceholderData
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject


class HomeViewModel @Inject constructor(
        notesRepository: NotesRepository,
        prefs: PrefsManager,
        val syncManager: SyncManager
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

    private val _showEmptyTrashDialogEvent = MutableLiveData<Event<Unit>>()
    val showEmptyTrashDialogEvent: LiveData<Event<Unit>>
        get() = _showEmptyTrashDialogEvent


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
        prefs.listLayoutMode = mode
    }

    fun emptyTrashPre() {
        if (listItems.isNotEmpty()) {
            _showEmptyTrashDialogEvent.send()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            notesRepository.emptyTrash()
        }
    }

    fun syncNotes() {
        viewModelScope.launch {
            syncManager.syncNotes(delay = PrefsManager.MIN_MANUAL_SYNC_INTERVAL) { e ->
                // Sync failed for unknown reason.
                Log.e(TAG, "Couldn't sync notes", e)
                _messageEvent.send(R.string.sync_failed_message)
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
        prefs.lastTrashReminderTime = System.currentTimeMillis()

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
                if (System.currentTimeMillis() - prefs.lastTrashReminderTime >
                        PrefsManager.TRASH_REMINDER_DELAY.toLongMilliseconds()) {
                    this += MessageItem(TRASH_REMINDER_ITEM_ID,
                            R.string.trash_reminder_message,
                            PrefsManager.TRASH_AUTO_DELETE_DELAY.inDays.toInt())
                }
            }

            // Add note items
            for (note in notes) {
                val checked = selectedNotes.any { it.id == note.id }
                this += NoteItem(note.id, note, checked, emptyList(), emptyList())
            }
        }
    }

    override fun updatePlaceholder() = when (noteStatus.value!!) {
        NoteStatus.ACTIVE -> PlaceholderData(R.drawable.ic_list, R.string.note_placeholder_active)
        NoteStatus.ARCHIVED -> PlaceholderData(R.drawable.ic_archive, R.string.note_placeholder_archived)
        NoteStatus.TRASHED -> PlaceholderData(R.drawable.ic_delete, R.string.note_placeholder_deleted)
    }


    companion object {
        private const val TRASH_REMINDER_ITEM_ID = -1L

        private val TAG = HomeViewModel::class.java.simpleName
    }

}
