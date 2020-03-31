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
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.main.adapter.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject


class MainViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val prefs: SharedPreferences) : ViewModel(), NoteAdapter.Callback {

    private var listItems: List<NoteListItem> = emptyList()
        set(value) {
            field = value
            _noteItems.value = value
        }

    private val selectedIds = mutableSetOf<Long>()

    private var noteListJob: Job? = null

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _noteItems = MutableLiveData<List<NoteListItem>>()
    val noteItems: LiveData<List<NoteListItem>>
        get() = _noteItems

    private val _listLayoutMode = MutableLiveData<NoteListLayoutMode>()
    val listLayoutMode: LiveData<NoteListLayoutMode>
        get() = _listLayoutMode

    private val _editItemEvent = MutableLiveData<Event<NoteItem>>()
    val editItemEvent: LiveData<Event<NoteItem>>
        get() = _editItemEvent

    private val _messageEvent = MutableLiveData<Event<MessageEvent>>()
    val messageEvent: LiveData<Event<MessageEvent>>
        get() = _messageEvent

    private val _selectedCount = MutableLiveData<Int>()
    val selectedCount: LiveData<Int>
        get() = _selectedCount


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

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    fun moveSelectedNotes() {
        changeSelectedNotesStatus(if (noteStatus.value == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun deleteSelectedNotes() {
        if (noteStatus.value == NoteStatus.TRASHED) {
            // Delete forever
            // TODO ask for confirmation

            viewModelScope.launch {
                val notes = selectedIds.mapNotNull { notesRepository.getById(it) }
                notesRepository.deleteNotes(notes)
                clearSelection()
            }

        } else {
            // Send to trash
            changeSelectedNotesStatus(NoteStatus.TRASHED)
        }
    }

    fun copySelectedNote(untitledName: String, copySuffix: String) {
        if (selectedIds.size != 1) {
            return
        }

        viewModelScope.launch {
            val note = notesRepository.getById(selectedIds.first()) ?: return@launch
            val date = Date()
            val copy = note.copy(
                    id = Note.NO_ID,
                    uuid = Note.generateNoteUuid(),
                    title = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix),
                    addedDate = date,
                    lastModifiedDate = date)
            notesRepository.insertNote(copy)
            clearSelection()
        }
    }

    private fun setAllSelected(selected: Boolean) {
        changeListItems { list ->
            for ((i, item) in list.withIndex()) {
                if (item is NoteItem && item.checked != selected) {
                    list[i] = item.copy(checked = selected)
                    if (selected) {
                        selectedIds += item.id
                    } else {
                        selectedIds -= item.id
                    }
                }
            }
        }
        _selectedCount.value = selectedIds.size
    }

    private fun changeSelectedNotesStatus(newStatus: NoteStatus) {
        if (selectedIds.isEmpty()) {
            return
        }

        viewModelScope.launch {
            // Change the status for all notes.
            val date = Date()
            val oldNotes = selectedIds.mapNotNull { notesRepository.getById(it) }
            val newNotes = oldNotes.map { it.copy(status = newStatus, lastModifiedDate = date) }
            notesRepository.updateNotes(newNotes)

            // Show status change message.
            val statusChange = StatusChange(oldNotes, oldNotes.first().status, newStatus)
            _messageEvent.value = Event(MessageEvent.StatusChangeEvent(statusChange))
            clearSelection()
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

    override fun onNoteItemClicked(item: NoteItem, pos: Int) {
        if (selectedIds.isEmpty()) {
            // Edit item
            _editItemEvent.value = Event(item)
        } else {
            // Toggle item selection
            toggleItemChecked(item, pos)
        }
    }

    override fun onNoteItemLongClicked(item: NoteItem, pos: Int) {
        toggleItemChecked(item, pos)
    }

    private fun toggleItemChecked(item: NoteItem, pos: Int) {
        val checked = !item.checked
        if (checked) {
            selectedIds += item.id
        } else {
            selectedIds -= item.id
        }
        _selectedCount.value = selectedIds.size

        changeListItems { it[pos] = item.copy(checked = checked) }
    }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        // Update last remind time when user dismisses message.
        prefs.edit().putLong(PreferenceHelper.LAST_TRASH_REMIND_TIME,
                System.currentTimeMillis()).apply()

        // Remove message item in list
        changeListItems { it.removeAt(pos) }
    }

    override val isNoteSwipeEnabled: Boolean
        get() = noteStatus.value == NoteStatus.ACTIVE && selectedIds.isEmpty()

    override fun onNoteSwiped(pos: Int) {
        // Archive note
        val oldNote = (noteItems.value!![pos] as NoteItem).note
        val newNote = oldNote.copy(status = NoteStatus.ARCHIVED, lastModifiedDate = Date())
        viewModelScope.launch {
            notesRepository.updateNote(newNote)
        }

        // Show message
        val statusChange = StatusChange(listOf(oldNote), NoteStatus.ACTIVE, NoteStatus.ARCHIVED)
        _messageEvent.value = Event(MessageEvent.StatusChangeEvent(statusChange))
    }

    private inline fun changeListItems(change: (MutableList<NoteListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    private fun createListItems(status: NoteStatus, notes: List<Note>) {
        listItems = buildList {
            if (status == NoteStatus.TRASHED && notes.isNotEmpty()) {
                // If needed, add reminder that notes get auto-deleted when in trash.
                val lastReminder = prefs.getLong(PreferenceHelper.LAST_TRASH_REMIND_TIME, 0)
                if (System.currentTimeMillis() - lastReminder >
                        PreferenceHelper.TRASH_REMINDER_DELAY * 86400000L) {
                    this += MessageItem(TRASH_REMINDER_ITEM_ID, R.string.message_trash_reminder,
                            PreferenceHelper.TRASH_AUTO_DELETE_DELAY)
                }
            }

            // Add note items
            for (note in notes) {
                this += NoteItem(note.id, note, note.id in selectedIds)
            }
        }
    }

    companion object {
        private const val TRASH_REMINDER_ITEM_ID = -1L
    }

}
