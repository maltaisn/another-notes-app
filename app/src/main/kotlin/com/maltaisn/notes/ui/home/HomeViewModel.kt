/*
 * Copyright 2022 Nicolas Maltais
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
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.SortSettings
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteWithLabels
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.setToStartOfDay
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.note.NoteItemFactory
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.PlaceholderData
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.HeaderItem
import com.maltaisn.notes.ui.note.adapter.MessageItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListItem
import com.maltaisn.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import debugCheck
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    notesRepository: NotesRepository,
    labelsRepository: LabelsRepository,
    prefs: PrefsManager,
    reminderAlarmManager: ReminderAlarmManager,
    noteItemFactory: NoteItemFactory,
    private val buildTypeBehavior: BuildTypeBehavior,
) : NoteViewModel(savedStateHandle, notesRepository, labelsRepository, prefs, noteItemFactory, reminderAlarmManager),
    NoteAdapter.Callback {

    var currentDestination: HomeDestination = HomeDestination.Status(NoteStatus.ACTIVE)
        private set

    private var batteryRestricted = false
    private var notificationsRestricted = false

    private val _fabShown = MutableLiveData<Boolean>()
    val fabShown: LiveData<Boolean>
        get() = _fabShown

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _createNoteEvent = MutableLiveData<Event<NewNoteSettings>>()
    val createNoteEvent: LiveData<Event<NewNoteSettings>>
        get() = _createNoteEvent

    private val _showEmptyTrashDialogEvent = MutableLiveData<Event<Unit>>()
    val showEmptyTrashDialogEvent: LiveData<Event<Unit>>
        get() = _showEmptyTrashDialogEvent

    init {
        restoreState()
    }

    fun setDestination(destination: HomeDestination) {
        currentDestination = destination
        updateNoteList()
        updateFabVisibility()
    }

    private fun updateNoteList() {
        // Update note items live data when database flow emits a list.
        val destination = currentDestination
        noteListJob?.cancel()
        noteListJob = viewModelScope.launch {
            waitForRestoredState()

            when (destination) {
                is HomeDestination.Status -> {
                    notesRepository.getNotesByStatus(destination.status).collect { notes ->
                        listItems = when (destination.status) {
                            NoteStatus.ACTIVE -> createActiveListItems(notes)
                            NoteStatus.ARCHIVED -> createArchivedListItems(notes)
                            NoteStatus.DELETED -> createDeletedListItems(notes)
                        }
                    }
                }
                is HomeDestination.Labels -> {
                    notesRepository.getNotesByLabel(destination.label.id).collect { notes ->
                        listItems = createLabelsListItems(notes, destination.label)
                    }
                }
                is HomeDestination.Reminders -> {
                    notesRepository.getNotesWithReminder().collect { notes ->
                        listItems = createRemindersListItems(notes)
                    }
                }
            }
        }
    }

    /** When user clicks on FAB. */
    fun createNote() {
        val destination = currentDestination
        _createNoteEvent.send(NewNoteSettings(
            if (destination is HomeDestination.Labels) destination.label.id else Label.NO_ID,
            destination is HomeDestination.Reminders
        ))
    }

    fun changeSort(settings: SortSettings) {
        prefs.sortField = settings.field
        prefs.sortDirection = settings.direction
        // Updating sorting preferences doesn't trigger the database Flow, update manually.
        updateNoteList()
    }

    override fun onListLayoutModeChanged() {
        // Updating the list layout mode doesn't trigger the database flow => Update manually.
        updateNoteList()
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

    /** Update restrictions status so that appropriate warnings may be shown to user. */
    fun updateRestrictions(battery: Boolean, notifications: Boolean) {
        val updateList = battery != batteryRestricted || notifications != notificationsRestricted
        batteryRestricted = battery
        notificationsRestricted = notifications
        if (updateList) {
            updateNoteList()
        }
    }

    fun doExtraAction() {
        viewModelScope.launch {
            buildTypeBehavior.doExtraAction(this@HomeViewModel)
        }
    }

    override fun updateNoteSelection() {
        super.updateNoteSelection()
        updateFabVisibility()
    }

    private fun updateFabVisibility() {
        _fabShown.value = when (val destination = currentDestination) {
            is HomeDestination.Status -> destination.status == NoteStatus.ACTIVE
            is HomeDestination.Labels -> true
            is HomeDestination.Reminders -> true
        } && selectedNotes.isEmpty()
    }

    override val selectedNoteStatus: NoteStatus?
        get() {
            val destination = currentDestination
            return if (destination is HomeDestination.Status) {
                // Only same status notes in this destination
                destination.status
            } else {
                // If one note is active, consider all active.
                // Otherwise consider archived.
                debugCheck(selectedNotes.none { it.status == NoteStatus.DELETED })
                when {
                    selectedNotes.isEmpty() -> null
                    selectedNotes.any { it.status == NoteStatus.ACTIVE } -> NoteStatus.ACTIVE
                    else -> NoteStatus.ARCHIVED
                }
            }
        }

    override fun onMessageItemDismissed(item: MessageItem, pos: Int) {
        val now = System.currentTimeMillis()
        when (item.id) {
            TRASH_REMINDER_ITEM_ID -> prefs.lastTrashReminderTime = now
            BATTERY_RESTRICTED_ITEM_ID -> prefs.lastRestrictedBatteryReminderTime = now
        }

        // Remove message item in list
        changeListItems { it.removeAt(pos) }
    }

    override fun getNoteSwipeAction(direction: NoteAdapter.SwipeDirection): SwipeAction {
        return if (currentDestination == HomeDestination.Status(NoteStatus.ACTIVE) && selectedNotes.isEmpty()) {
            when (direction) {
                NoteAdapter.SwipeDirection.LEFT -> prefs.swipeActionLeft
                NoteAdapter.SwipeDirection.RIGHT -> prefs.swipeActionRight
            }
        } else {
            SwipeAction.NONE
        }
    }

    override fun onNoteSwiped(pos: Int, direction: NoteAdapter.SwipeDirection) {
        val note = (noteItems.value!![pos] as NoteItem).note
        changeNotesStatus(setOf(note), when (getNoteSwipeAction(direction)) {
            SwipeAction.ARCHIVE -> NoteStatus.ARCHIVED
            SwipeAction.DELETE -> NoteStatus.DELETED
            SwipeAction.NONE -> return  // should not happen
        })
    }

    override fun onNoteActionButtonClicked(item: NoteItem, pos: Int) {
        // Action button is only shown for "Mark as done" in Reminders screen.
        // So mark reminder as done.
        viewModelScope.launch {
            val note = item.note
            notesRepository.updateNote(note.copy(reminder = note.reminder?.markAsDone()))
        }
    }

    private fun createActiveListItems(notes: List<NoteWithLabels>): List<NoteListItem> = buildList {
        if (prefs.autoExportFailed) {
            this += MessageItem(AUTO_EXPORT_FAIL_ITEM_ID, R.string.auto_export_fail)
        }

        // Separate pinned and not pinned notes with headers
        var addedPinnedHeader = false
        var addedNotPinnedHeader = false

        if (notes.isNotEmpty() && notes.first().note.pinned == PinnedStatus.PINNED) {
            this += PINNED_HEADER_ITEM
            addedPinnedHeader = true
        }

        for (note in notes) {
            if (addedPinnedHeader && !addedNotPinnedHeader &&
                note.note.pinned == PinnedStatus.UNPINNED
            ) {
                this += NOT_PINNED_HEADER_ITEM
                addedNotPinnedHeader = true
            }
            addNoteItem(note)
        }
    }

    private fun createArchivedListItems(notes: List<NoteWithLabels>) = buildList {
        for (note in notes) {
            addNoteItem(note)
        }
    }

    private fun createDeletedListItems(notes: List<NoteWithLabels>) = buildList {
        // If needed, add reminder that notes get auto-deleted when in trash.
        if (notes.isNotEmpty() &&
            System.currentTimeMillis() - prefs.lastTrashReminderTime >
            PrefsManager.TRASH_REMINDER_DELAY.inWholeMilliseconds
        ) {
            this += MessageItem(TRASH_REMINDER_ITEM_ID,
                R.string.trash_reminder_message,
                listOf(PrefsManager.TRASH_AUTO_DELETE_DELAY.inWholeDays))
        }

        for (note in notes) {
            addNoteItem(note)
        }
    }

    private fun createLabelsListItems(notes: List<NoteWithLabels>, label: Label) = buildList {
        // Separate pinned, active and archived notes with headers
        var addedPinnedHeader = false
        var addedNotPinnedHeader = false
        var addedArchivedHeader = false

        if (notes.isNotEmpty() && notes.first().note.pinned == PinnedStatus.PINNED) {
            this += PINNED_HEADER_ITEM
            addedPinnedHeader = true
        }

        for (noteWithLabels in notes) {
            val note = noteWithLabels.note

            // Add headers if necessary
            if (!addedArchivedHeader && note.status == NoteStatus.ARCHIVED) {
                this += ARCHIVED_HEADER_ITEM
                addedArchivedHeader = true
            } else if (addedPinnedHeader && !addedNotPinnedHeader &&
                note.pinned == PinnedStatus.UNPINNED
            ) {
                this += NOT_PINNED_HEADER_ITEM
                addedNotPinnedHeader = true
            }

            // Omit the filtered label from the note since all notes have it.
            addNoteItem(noteWithLabels, excludeLabel = label)
        }
    }

    private fun createRemindersListItems(notes: List<NoteWithLabels>) = buildList {
        val calendar = Calendar.getInstance()
        calendar.setToStartOfDay()
        calendar.add(Calendar.DATE, 1)
        val endOfToday = calendar.timeInMillis
        val now = System.currentTimeMillis()

        // If needed, add warning that notifications won't work properly if battery is restricted.
        if (batteryRestricted && notes.isNotEmpty() &&
            now - prefs.lastRestrictedBatteryReminderTime >
            PrefsManager.RESTRICTED_BATTERY_REMINDER_DELAY.inWholeMilliseconds
        ) {
            this += MessageItem(BATTERY_RESTRICTED_ITEM_ID, R.string.reminder_restricted_battery)
        }

        // If needed, add warning that notification permission has been denied.
        if (notes.isNotEmpty() && notificationsRestricted) {
            this += MessageItem(NOTIFICATION_DENIED_ITEM_ID, R.string.reminder_notif_permission_denied)
        }

        var addedOverdueHeader = false
        var addedTodayHeader = false
        var addedUpcomingHeader = false

        for (noteWithLabels in notes) {
            val reminderTime = (noteWithLabels.note.reminder ?: continue).next.time

            if (!addedOverdueHeader && reminderTime <= now) {
                // Reminder is past, add overdue header before it
                this += OVERDUE_HEADER_ITEM
                addedOverdueHeader = true
            } else if (!addedTodayHeader && reminderTime > now && reminderTime < endOfToday) {
                // Reminder is today but hasn't happened yet, add today header before it.
                this += TODAY_HEADER_ITEM
                addedTodayHeader = true
            } else if (!addedUpcomingHeader && reminderTime >= endOfToday) {
                // Reminder is after the end of today, add upcoming header before it.
                this += UPCOMING_HEADER_ITEM
                addedUpcomingHeader = true
            }

            // Show "Mark as done" action button.
            addNoteItem(noteWithLabels, showMarkAsDone = reminderTime <= now)
        }
    }

    private fun MutableList<NoteListItem>.addNoteItem(
        noteWithLabels: NoteWithLabels,
        showMarkAsDone: Boolean = false,
        excludeLabel: Label? = null
    ) {
        val note = noteWithLabels.note
        val checked = isNoteSelected(note)
        val labels = if (excludeLabel == null) {
            noteWithLabels.labels
        } else {
            noteWithLabels.labels - excludeLabel
        }
        this += noteItemFactory.createItem(note, labels, checked, showMarkAsDone)
    }

    override fun updatePlaceholder() = when (val destination = currentDestination) {
        is HomeDestination.Status -> when (destination.status) {
            NoteStatus.ACTIVE -> PlaceholderData(R.drawable.ic_list,
                R.string.note_placeholder_active)
            NoteStatus.ARCHIVED -> PlaceholderData(R.drawable.ic_archive,
                R.string.note_placeholder_archived)
            NoteStatus.DELETED -> PlaceholderData(R.drawable.ic_delete,
                R.string.note_placeholder_deleted)
        }
        is HomeDestination.Reminders -> {
            PlaceholderData(R.drawable.ic_alarm, R.string.reminder_empty_placeholder)
        }
        is HomeDestination.Labels -> {
            PlaceholderData(R.drawable.ic_label_outline, R.string.label_notes_empty_placeholder)
        }
    }

    data class NewNoteSettings(val labelId: Long, val initialReminder: Boolean)

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<HomeViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): HomeViewModel
    }

    companion object {
        private const val TRASH_REMINDER_ITEM_ID = -1L
        private const val BATTERY_RESTRICTED_ITEM_ID = -8L
        private const val AUTO_EXPORT_FAIL_ITEM_ID = -9L
        private const val NOTIFICATION_DENIED_ITEM_ID = -10L

        val PINNED_HEADER_ITEM = HeaderItem(-2, R.string.note_pinned)
        val NOT_PINNED_HEADER_ITEM = HeaderItem(-3, R.string.note_not_pinned)
        val TODAY_HEADER_ITEM = HeaderItem(-4, R.string.reminder_today)
        val OVERDUE_HEADER_ITEM = HeaderItem(-5, R.string.reminder_overdue)
        val UPCOMING_HEADER_ITEM = HeaderItem(-6, R.string.reminder_upcoming)
        val ARCHIVED_HEADER_ITEM = HeaderItem(-7, R.string.note_location_archived)
    }
}
