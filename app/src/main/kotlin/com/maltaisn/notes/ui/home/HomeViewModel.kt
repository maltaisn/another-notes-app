/*
 * Copyright 2021 Nicolas Maltais
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.PlaceholderData
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.HeaderItem
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListItem
import com.maltaisn.notes.ui.send
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Calendar

class HomeViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    notesRepository: NotesRepository,
    prefs: PrefsManager,
    private val buildTypeBehavior: BuildTypeBehavior
) : NoteViewModel(savedStateHandle, notesRepository, prefs), NoteAdapter.Callback {

    private var noteListJob: Job? = null

    private val _destination = MutableLiveData<HomeDestination>()
    val destination: LiveData<HomeDestination>
        get() = _destination

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _showEmptyTrashDialogEvent = MutableLiveData<Event<Unit>>()
    val showEmptyTrashDialogEvent: LiveData<Event<Unit>>
        get() = _showEmptyTrashDialogEvent

    init {
        viewModelScope.launch {
            restoreState()

            setDestination(destination.value!!)
        }
    }

    override suspend fun restoreState() {
        _destination.value = savedStateHandle.get(KEY_NOTE_STATUS) ?: HomeDestination.ACTIVE
        super.restoreState()
    }

    fun setDestination(destination: HomeDestination) {
        _destination.value = destination

        savedStateHandle.set(KEY_NOTE_STATUS, destination)

        // Cancel previous flow collection
        noteListJob?.cancel()

        // Update note items live data when database flow emits a list.
        noteListJob = viewModelScope.launch {
            val noteStatus = destination.noteStatus
            if (noteStatus == null) {
                // Reminders
                notesRepository.getNotesWithReminderSorted().collect { notes ->
                    listItems = createRemindersListItems(notes)
                }
            } else {
                notesRepository.getNotesByStatus(noteStatus).collect { notes ->
                    listItems = when (noteStatus) {
                        NoteStatus.ACTIVE -> createActiveListItems(notes)
                        NoteStatus.ARCHIVED -> createArchivedListItems(notes)
                        NoteStatus.DELETED -> createDeletedListItems(notes)
                    }
                    yield()
                }
            }
        }
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

    fun doExtraAction() {
        viewModelScope.launch {
            buildTypeBehavior.doExtraAction(this@HomeViewModel)
        }
    }

    override val selectedNoteStatus: NoteStatus?
        // There can only be notes of one status selected in this fragment.
        get() {
            val noteStatus = destination.value?.noteStatus
            return when {
                noteStatus != null -> noteStatus
                selectedNotes.isEmpty() -> null
                selectedNotes.any { it.status == NoteStatus.ACTIVE } -> NoteStatus.ACTIVE
                else -> NoteStatus.ARCHIVED
            }
        }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        prefs.lastTrashReminderTime = System.currentTimeMillis()

        // Remove message item in list
        changeListItems { it.removeAt(pos) }
    }

    override val isNoteSwipeEnabled: Boolean
        get() = destination.value == HomeDestination.ACTIVE && selectedNotes.isEmpty()

    override fun onNoteSwiped(pos: Int) {
        // Archive note
        val note = (noteItems.value!![pos] as NoteItem).note
        changeNotesStatus(setOf(note), when (prefs.swipeAction) {
            SwipeAction.ARCHIVE -> NoteStatus.ARCHIVED
            SwipeAction.DELETE -> NoteStatus.DELETED
        })
    }

    private fun createActiveListItems(notes: List<Note>): List<NoteListItem> = buildList {
        // If there's at least one pinned note, add pinned header.
        if (notes.isNotEmpty() && notes.first().pinned == PinnedStatus.PINNED) {
            this += PINNED_HEADER_ITEM
            for (note in notes) {
                if (note.pinned != PinnedStatus.PINNED) {
                    break
                }
                addNoteItem(note)
            }
            if (this.size <= notes.size) {
                // Add another header for other notes if there's at least one not pinned.
                this += NOT_PINNED_HEADER_ITEM
            }
        }

        for (note in notes) {
            if (note.pinned == PinnedStatus.PINNED) continue
            addNoteItem(note)
        }
    }

    private fun createArchivedListItems(notes: List<Note>): List<NoteListItem> = buildList {
        for (note in notes) {
            addNoteItem(note)
        }
    }

    private fun createDeletedListItems(notes: List<Note>): List<NoteListItem> = buildList {
        // If needed, add reminder that notes get auto-deleted when in trash.
        if (notes.isNotEmpty() &&
            System.currentTimeMillis() - prefs.lastTrashReminderTime >
            PrefsManager.TRASH_REMINDER_DELAY.toLongMilliseconds()
        ) {
            this += MessageItem(TRASH_REMINDER_ITEM_ID,
                R.string.trash_reminder_message,
                listOf(PrefsManager.TRASH_AUTO_DELETE_DELAY.inDays.toInt()))
        }

        for (note in notes) {
            addNoteItem(note)
        }
    }

    private fun createRemindersListItems(notes: List<Note>): List<NoteListItem> = buildList {
        val calendar = Calendar.getInstance()
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MILLISECOND] = 0
        calendar.add(Calendar.DATE, 1)
        val endOfToday = calendar.timeInMillis
        val now = System.currentTimeMillis()

        var addedOverdueHeader = false
        var addedTodayHeader = false
        var addedUpcomingHeader = false
        for (note in notes) {
            val reminderTime = (note.reminder ?: continue).next.time

            if (!addedOverdueHeader && reminderTime <= now) {
                // Reminder is past, add overdue header before it
                this += OVERDUE_HEADER_ITEM
                addedOverdueHeader = true
            } else if (!addedTodayHeader && reminderTime > now && reminderTime < endOfToday) {
                // Reminder is today but hasn't happened yet, add today header before it.
                this += TODAY_HEADER_ITEM
                addedTodayHeader = true
            } else if (!addedUpcomingHeader && reminderTime > endOfToday) {
                // Reminder is after the end of today, add upcoming header before it.
                this += UPCOMING_HEADER_ITEM
                addedUpcomingHeader = true
            }

            addNoteItem(note)
        }
    }

    private fun MutableList<NoteListItem>.addNoteItem(note: Note) {
        val checked = isNoteSelected(note)
        this += NoteItem(note.id, note, checked)
    }

    override fun updatePlaceholder() = when (destination.value!!) {
        HomeDestination.ACTIVE -> PlaceholderData(R.drawable.ic_list,
            R.string.note_placeholder_active)
        HomeDestination.ARCHIVED -> PlaceholderData(R.drawable.ic_archive,
            R.string.note_placeholder_archived)
        HomeDestination.DELETED -> PlaceholderData(R.drawable.ic_delete,
            R.string.note_placeholder_deleted)
        HomeDestination.REMINDERS -> PlaceholderData(
            R.drawable.ic_alarm, R.string.reminder_empty_placeholder)
    }

    @AssistedInject.Factory
    interface Factory : AssistedSavedStateViewModelFactory<HomeViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): HomeViewModel
    }

    companion object {
        private const val TRASH_REMINDER_ITEM_ID = -1L

        val PINNED_HEADER_ITEM = HeaderItem(-2, R.string.note_pinned)
        val NOT_PINNED_HEADER_ITEM = HeaderItem(-3, R.string.note_not_pinned)
        val TODAY_HEADER_ITEM = HeaderItem(-4, R.string.reminder_today)
        val OVERDUE_HEADER_ITEM = HeaderItem(-5, R.string.reminder_overdue)
        val UPCOMING_HEADER_ITEM = HeaderItem(-6, R.string.reminder_upcoming)

        private const val KEY_NOTE_STATUS = "note_status"
    }
}
