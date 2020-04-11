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

package com.maltaisn.notes.ui.note

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.note.adapter.*
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.launch
import java.util.*


abstract class NoteViewModel(
        protected val notesRepository: NotesRepository,
        protected val prefs: SharedPreferences) : ViewModel(), NoteAdapter.Callback {

    protected var listItems: List<NoteListItem> = emptyList()
        set(value) {
            field = value
            _noteItems.value = value
        }

    protected val selectedNotes = mutableSetOf<Note>()
    protected abstract val selectedNoteStatus: NoteStatus?

    private val _noteItems = MutableLiveData<List<NoteListItem>>()
    val noteItems: LiveData<List<NoteListItem>>
        get() = _noteItems

    protected val _listLayoutMode = MutableLiveData<NoteListLayoutMode>()
    val listLayoutMode: LiveData<NoteListLayoutMode>
        get() = _listLayoutMode

    private val _editItemEvent = MutableLiveData<Event<NoteItem>>()
    val editItemEvent: LiveData<Event<NoteItem>>
        get() = _editItemEvent

    private val _shareEvent = MutableLiveData<Event<ShareData>>()
    val shareEvent: LiveData<Event<ShareData>>
        get() = _shareEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _currentSelection = MutableLiveData<NoteSelection>()
    val currentSelection: LiveData<NoteSelection>
        get() = _currentSelection


    init {
        val layoutModeVal = prefs.getInt(PreferenceHelper.LIST_LAYOUT_MODE,
                NoteListLayoutMode.LIST.value)
        _listLayoutMode.value = NoteListLayoutMode.values().find { it.value == layoutModeVal }
    }

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    fun moveSelectedNotes() {
        changeSelectedNotesStatus(if (selectedNoteStatus == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun deleteSelectedNotes() {
        if (selectedNoteStatus == NoteStatus.TRASHED) {
            // Delete forever
            // TODO ask for confirmation

            viewModelScope.launch {
                notesRepository.deleteNotes(selectedNotes.toList())
                clearSelection()
            }

        } else {
            // Send to trash
            changeSelectedNotesStatus(NoteStatus.TRASHED)
        }
    }

    fun copySelectedNote(untitledName: String, copySuffix: String) {
        if (selectedNotes.size != 1) {
            return
        }

        viewModelScope.launch {
            val note = selectedNotes.first()
            val date = Date()
            val copy = note.copy(
                    id = Note.NO_ID,
                    uuid = Note.generateNoteUuid(),
                    title = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix),
                    addedDate = date,
                    lastModifiedDate = date,
                    changed = true)
            notesRepository.insertNote(copy)
            clearSelection()
        }
    }

    fun shareNote() {
        if (selectedNotes.isEmpty()) {
            return
        }
        val note = selectedNotes.first()
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    private fun setAllSelected(selected: Boolean) {
        if (!selected && selectedNotes.isEmpty()) {
            // Already all unselected.
            return
        }

        val selectedBefore = selectedNotes.size
        val newList = listItems.toMutableList()
        for ((i, item) in listItems.withIndex()) {
            if (item is NoteItem && item.checked != selected) {
                newList[i] = item.copy(checked = selected)
                if (selected) {
                    selectedNotes += item.note
                } else {
                    selectedNotes -= item.note
                }
            }
        }

        if (selectedBefore != selectedNotes.size) {
            // If selection changed, update list and counter.
            listItems = newList
            _currentSelection.value = NoteSelection(selectedNotes.size, selectedNoteStatus)
        }
    }

    protected fun changeNotesStatus(notes: Set<Note>, newStatus: NoteStatus) {
        if (notes.isEmpty()) {
            return
        }

        // Update the status in database
        viewModelScope.launch {
            val date = Date()
            val oldNotes = notes.toList()
            val newNotes = oldNotes.map { note ->
                note.copy(status = newStatus,
                        lastModifiedDate = date,
                        changed = true)
            }
            notesRepository.updateNotes(newNotes)

            // Show status change message.
            val statusChange = StatusChange(oldNotes, oldNotes.first().status, newStatus)
            _statusChangeEvent.send(statusChange)
        }
    }

    private fun changeSelectedNotesStatus(newStatus: NoteStatus) {
        if (selectedNotes.isNotEmpty()) {
            changeNotesStatus(selectedNotes, newStatus)
            clearSelection()
        }
    }

    override fun onNoteItemClicked(item: NoteItem, pos: Int) {
        if (selectedNotes.isEmpty()) {
            // Edit item
            _editItemEvent.send(item)
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
            selectedNotes += item.note
        } else {
            selectedNotes -= item.note
        }
        _currentSelection.value = NoteSelection(selectedNotes.size, selectedNoteStatus)

        changeListItems { it[pos] = item.copy(checked = checked) }
    }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        // Do nothing.
    }

    override val isNoteSwipeEnabled = false

    override fun onNoteSwiped(pos: Int) {
        // Do nothing.
    }

    protected inline fun changeListItems(change: (MutableList<NoteListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    data class NoteSelection(val count: Int, val status: NoteStatus?)

}
