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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.launch
import java.util.Date

/**
 * This view model provides common behavior for home and search view models.
 */
abstract class NoteViewModel(
    protected val savedStateHandle: SavedStateHandle,
    protected val notesRepository: NotesRepository,
    protected val prefs: PrefsManager
) : ViewModel(), NoteAdapter.Callback {

    protected var listItems: List<NoteListItem> = emptyList()
        set(value) {
            field = value
            _noteItems.value = value

            _placeholderData.value = if (value.isEmpty()) {
                updatePlaceholder()
            } else {
                null
            }

            // Update selected notes.
            val selectedBefore = selectedNotes.size
            _selectedNotes.clear()
            for (item in value) {
                if (item is NoteItem && item.checked) {
                    _selectedNotes += item.note
                }
            }

            updateNoteSelection()
            if (selectedNotes.size != selectedBefore) {
                saveNoteSelectionState()
            }
        }

    private val _selectedNotes = mutableSetOf<Note>()
    protected val selectedNotes: Set<Note> get() = _selectedNotes

    /**
     * Implementation should return the "global" status of the selected notes.
     * This is used to determine what move event will do on the selected notes.
     */
    protected abstract val selectedNoteStatus: NoteStatus?

    private val _noteItems = MutableLiveData<List<NoteListItem>>()
    val noteItems: LiveData<List<NoteListItem>>
        get() = _noteItems

    private val _listLayoutMode = MutableLiveData<NoteListLayoutMode>()
    val listLayoutMode: LiveData<NoteListLayoutMode>
        get() = _listLayoutMode

    private val _editItemEvent = MutableLiveData<Event<Long>>()
    val editItemEvent: LiveData<Event<Long>>
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

    private val _placeholderData = MutableLiveData<PlaceholderData?>(null)
    val placeholderData: LiveData<PlaceholderData?>
        get() = _placeholderData

    private val _showDeletedForeverConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeletedForeverConfirmEvent

    init {
        // Initialize list layout to saved value.
        _listLayoutMode.value = prefs.listLayoutMode
    }

    /**
     * Restore the state of this fragment from [savedStateHandle].
     * This must be called by subclass on initialization. It's not called here because
     * it's suspending, so state might be restored *after* child is initialized...
     */
    protected open suspend fun restoreState() {
        // Restore saved selected notes
        val selectedIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_IDS) ?: return
        _selectedNotes += selectedIds.mapNotNull { notesRepository.getById(it) }
        updateNoteSelection()
    }

    /**
     * Called when note list is empty to update the placeholder data.
     */
    abstract fun updatePlaceholder(): PlaceholderData

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    fun togglePin() {
        // If one note in selection isn't pinned, pin all. If all are pinned, unpin all.
        viewModelScope.launch {
            val date = Date()
            val newPinned = if (selectedNotes.any { it.pinned == PinnedStatus.UNPINNED }) {
                PinnedStatus.PINNED
            } else {
                PinnedStatus.UNPINNED
            }
            val newNotes = selectedNotes.mapNotNull { note ->
                if (note.pinned != PinnedStatus.CANT_PIN && note.pinned != newPinned) {
                    note.copy(pinned = newPinned, lastModifiedDate = date)
                } else {
                    null
                }
            }
            notesRepository.updateNotes(newNotes)
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

    fun moveSelectedNotes() {
        changeSelectedNotesStatus(if (selectedNoteStatus == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun deleteSelectedNotesPre() {
        if (selectedNoteStatus == NoteStatus.DELETED) {
            // Ask user for confirmation before deleting selected notes forever.
            _showDeletedForeverConfirmEvent.send()
        } else {
            // Send to trash
            changeSelectedNotesStatus(NoteStatus.DELETED)
        }
    }

    fun deleteSelectedNotes() {
        // Delete forever
        viewModelScope.launch {
            notesRepository.deleteNotes(selectedNotes.toList())
            clearSelection()
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
                title = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix),
                addedDate = date,
                lastModifiedDate = date)
            notesRepository.insertNote(copy)
            clearSelection()
        }
    }

    fun shareSelectedNote() {
        val note = selectedNotes.firstOrNull() ?: return
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    /** Set the selected state of all notes to [selected]. */
    private fun setAllSelected(selected: Boolean) {
        if (!selected && selectedNotes.isEmpty()) {
            // Already all unselected.
            return
        }

        changeListItems {
            for ((i, item) in it.withIndex()) {
                if (item is NoteItem && item.checked != selected) {
                    it[i] = item.copy(checked = selected)
                }
            }
        }
    }

    protected fun isNoteSelected(note: Note): Boolean {
        // Compare IDs because Note object can change and not be equal.
        return selectedNotes.any { it.id == note.id }
    }

    /** Update current selection live data to reflect current selection. */
    private fun updateNoteSelection() {
        // If no pinnable (active) notes are selected, selection is unpinnable.
        // If at least one unpinned note is selected, selection is unpinned.
        // Otherwise selection is pinned.
        val pinned = when {
            selectedNotes.none { it.status == NoteStatus.ACTIVE } -> PinnedStatus.CANT_PIN
            selectedNotes.any { it.pinned == PinnedStatus.UNPINNED } -> PinnedStatus.UNPINNED
            else -> PinnedStatus.PINNED
        }

        // If any note has a reminder, consider whole selection has a reminder,
        // so the single note reminder can be deleted.
        val hasReminder = selectedNotes.any { it.reminder != null }

        _currentSelection.value = NoteSelection(selectedNotes.size, selectedNoteStatus, pinned, hasReminder)
    }

    /** Save [selectedNotes] to [savedStateHandle]. */
    private fun saveNoteSelectionState() {
        savedStateHandle.set(KEY_SELECTED_IDS, selectedNotes.mapTo(ArrayList()) { it.id })
    }

    /** Change the status of [notes] to [newStatus]. */
    protected fun changeNotesStatus(notes: Set<Note>, newStatus: NoteStatus) {
        val oldNotes = notes
            .filter { it.status != newStatus }
            .ifEmpty { return }

        val date = Date()
        val newNotes = oldNotes.map { note ->
            note.copy(status = newStatus, lastModifiedDate = date,
                pinned = if (newStatus == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN)
        }

        // Update the status in database
        viewModelScope.launch {
            notesRepository.updateNotes(newNotes)

            // Show status change message.
            val statusChange = StatusChange(oldNotes, oldNotes.first().status, newStatus)
            _statusChangeEvent.send(statusChange)
        }
    }

    /** Change the status of selected notes to [newStatus], and clear selection. */
    private fun changeSelectedNotesStatus(newStatus: NoteStatus) {
        changeNotesStatus(selectedNotes, newStatus)
        clearSelection()
    }

    override fun onNoteItemClicked(item: NoteItem, pos: Int) {
        if (selectedNotes.isEmpty()) {
            // Edit item
            _editItemEvent.send(item.note.id)
        } else {
            // Toggle item selection
            toggleItemChecked(item, pos)
        }
    }

    override fun onNoteItemLongClicked(item: NoteItem, pos: Int) {
        toggleItemChecked(item, pos)
    }

    private fun toggleItemChecked(item: NoteItem, pos: Int) {
        // Set the item as checked and update the list.
        changeListItems {
            it[pos] = item.copy(checked = !item.checked)
        }
    }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        // Do nothing.
    }

    override val isNoteSwipeEnabled = false

    override fun onNoteSwiped(pos: Int) {
        // Do nothing.
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    protected inline fun changeListItems(change: (MutableList<NoteListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    data class NoteSelection(
        val count: Int,
        val status: NoteStatus?,
        val pinned: PinnedStatus,
        val hasReminder: Boolean
    )

    companion object {
        private const val KEY_SELECTED_IDS = "selected_ids"
    }
}
