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

import androidx.lifecycle.*
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.converter.NoteStatusConverter
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.PlaceholderData
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.send
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class HomeViewModel @AssistedInject constructor(
        @Assisted savedStateHandle: SavedStateHandle,
        notesRepository: NotesRepository,
        prefs: PrefsManager,
        private val refreshBehavior: NoteRefreshBehavior,
        private val buildTypeBehavior: BuildTypeBehavior
) : NoteViewModel(savedStateHandle, notesRepository, prefs), NoteAdapter.Callback {

    private var noteListJob: Job? = null

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    val canRefresh = refreshBehavior.canRefreshChannel
            .asFlow().asLiveData(viewModelScope.coroutineContext)

    private val _stopRefreshEvent = MutableLiveData<Event<Unit>>()
    val stopRefreshEvent: LiveData<Event<Unit>>
        get() = _stopRefreshEvent

    private val _showEmptyTrashDialogEvent = MutableLiveData<Event<Unit>>()
    val showEmptyTrashDialogEvent: LiveData<Event<Unit>>
        get() = _showEmptyTrashDialogEvent

    init {
        viewModelScope.launch {
            restoreState()

            setNoteStatus(noteStatus.value!!)

            refreshBehavior.start()
        }
    }

    override suspend fun restoreState() {
        _noteStatus.value = NoteStatusConverter.toStatus(
                savedStateHandle.get(KEY_NOTE_STATUS) ?: NoteStatus.ACTIVE.value)
        super.restoreState()
    }

    fun setNoteStatus(status: NoteStatus) {
        _noteStatus.value = status

        savedStateHandle.set(KEY_NOTE_STATUS, NoteStatusConverter.toInt(status))

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

    /** When user clicks on empty trash. */
    fun emptyTrashPre() {
        if (listItems.isNotEmpty()) {
            _showEmptyTrashDialogEvent.send()
        }
    }

    /** When user confirms emptying trash. */
    fun emptyTrash() {
        viewModelScope.launch {
            notesRepository.emptyTrash()
        }
    }

    fun refreshNotes() {
        viewModelScope.launch {
            val message = refreshBehavior.refreshNotes()
            if (message != null) {
                _messageEvent.send(message)
            }
            _stopRefreshEvent.send()
        }
    }

    fun doExtraAction() {
        viewModelScope.launch {
            buildTypeBehavior.doExtraAction(this@HomeViewModel)
        }
    }

    override val selectedNoteStatus: NoteStatus?
        // There can only be notes of one status selected in this fragment.
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
                val checked = isNoteSelected(note)
                this += NoteItem(note.id, note, checked, emptyList(), emptyList())
            }
        }
    }

    override fun updatePlaceholder() = when (noteStatus.value!!) {
        NoteStatus.ACTIVE -> PlaceholderData(R.drawable.ic_list, R.string.note_placeholder_active)
        NoteStatus.ARCHIVED -> PlaceholderData(R.drawable.ic_archive, R.string.note_placeholder_archived)
        NoteStatus.TRASHED -> PlaceholderData(R.drawable.ic_delete, R.string.note_placeholder_deleted)
    }

    @AssistedInject.Factory
    interface Factory : AssistedSavedStateViewModelFactory<HomeViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): HomeViewModel
    }

    companion object {
        private const val TRASH_REMINDER_ITEM_ID = -1L

        private const val KEY_NOTE_STATUS = "note_status"
    }

}
