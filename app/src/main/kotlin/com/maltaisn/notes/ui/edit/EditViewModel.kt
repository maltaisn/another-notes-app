/*
 * Copyright 2025 Nicolas Maltais
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

package com.maltaisn.notes.ui.edit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.FractionalIndex
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability
import com.maltaisn.notes.ui.edit.actions.EditActionsAvailability
import com.maltaisn.notes.ui.edit.adapter.EditAdapter
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditChipsItem
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTextItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.event.BatchEvent
import com.maltaisn.notes.ui.edit.event.EditEvent
import com.maltaisn.notes.ui.edit.event.EventPayload
import com.maltaisn.notes.ui.edit.event.FocusChangeEvent
import com.maltaisn.notes.ui.edit.event.ItemAddEvent
import com.maltaisn.notes.ui.edit.event.ItemChangeEventItem
import com.maltaisn.notes.ui.edit.event.ItemCheckEvent
import com.maltaisn.notes.ui.edit.event.ItemEditEvent
import com.maltaisn.notes.ui.edit.event.ItemRemoveEvent
import com.maltaisn.notes.ui.edit.event.ItemReorderEvent
import com.maltaisn.notes.ui.edit.event.ItemSwapEvent
import com.maltaisn.notes.ui.edit.event.NoteConversionEvent
import com.maltaisn.notes.ui.edit.event.TextChangeEvent
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.ui.send
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Collections
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * View model for the edit note screen.
 */
@HiltViewModel
class EditViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefs: PrefsManager,
    private val reminderAlarmManager: ReminderAlarmManager,
    private val editableTextProvider: EditableTextProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), EditAdapter.Callback {

    /**
     * Whether the current note is a new note.
     * This is important to remember as to not recreate as new blank note
     * when [start] is called a second time.
     */
    private var isNewNote = false

    /**
     * Note being edited by user. This note data is not up-to-date with the UI.
     * - Call [updateNote] to update it to reflect UI state.
     * - Call [saveNote] to update it from UI and update database.
     */
    private var note = BLANK_NOTE

    /**
     * List of labels on note. Always reflects the UI.
     */
    private var labels = emptyList<Label>()

    /**
     * Status of the note being edited. This is separate from [note] so that
     * note status can be updated from this in [updateNote].
     */
    private var status = note.status

    /**
     * Whether the note being edited is pinned or not.
     */
    private var pinned = note.pinned

    /**
     * The reminder set on the note, or `null` if none is set.
     */
    private var reminder: Reminder? = null

    /**
     * URL of last clicked span, if any.
     */
    private var linkUrl: String?
        get() = savedStateHandle[KEY_LINK_URL]
        set(value) {
            savedStateHandle[KEY_LINK_URL] = value
        }

    /**
     * The currently displayed list items created in [recreateListItems].
     *
     * While this list is mutable, any in place changes should be reported to the adapter! This is used in the case
     * of moving items, where the view model updates the list but the adapter already knows of the move.
     *
     * Note the in the case of list items, a specific item has no identity. Its position and its content
     * can change at any time, so it can't be associated with a stable ID. This is problematic for item diff callback
     * and animations, which would rely on ID. Instead, the diff callback was made to rely on identity so that
     * add/remove animations take place correctly. The recycler view was also set up to not animate item appearance,
     * so that if the list is completely recreated, no animation will occur despite all items identity being lost.
     *
     * To allow restoring list note items original positions when checking and unchecking (if move to bottom is set),
     * each list note item carries an `actualPos` field which is the item actual position in the list note.
     * This field must be kept up-to-date after all changes to the list!
     */
    private val listItems: MutableList<EditListItem> = mutableListOf()

    /**
     * Class used to manage the undo queue.
     */
    private val undoManager = EditUndoManager().apply {
        maxEvents = MAX_UNDO_EVENTS
    }

    private val eventPayload: EventPayload
        get() = EventPayload(editableTextProvider, listItems, prefs.moveCheckedToBottom)

    /** Flag used during undo or redo operation to ignore text change callbacks. */
    private var ignoreTextChanges = false

    /** Job used to debounce end of batch for new events. */
    private var undoAppendJob: Job? = null

    private val _editActionsAvailability = MutableLiveData<EditActionsAvailability>()
    val editActionsAvailability: LiveData<EditActionsAvailability>
        get() = _editActionsAvailability

    private val _editItems = MutableLiveData<MutableList<EditListItem>>()
    val editItems: LiveData<out List<EditListItem>>
        get() = _editItems

    private val _noteCreateEvent = MutableLiveData<Event<Long>>()
    val noteCreateEvent: LiveData<Event<Long>>
        get() = _noteCreateEvent

    private val _focusEvent = MutableLiveData<Event<EditFocusChange>>()
    val focusEvent: LiveData<Event<EditFocusChange>>
        get() = _focusEvent

    private val _messageEvent = MutableLiveData<Event<EditMessage>>()
    val messageEvent: LiveData<Event<EditMessage>>
        get() = _messageEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _shareEvent = MutableLiveData<Event<ShareData>>()
    val shareEvent: LiveData<Event<ShareData>>
        get() = _shareEvent

    private val _showDeleteConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeleteConfirmEvent

    private val _showRemoveCheckedConfirmEvent = MutableLiveData<Event<Unit>>()
    val showRemoveCheckedConfirmEvent: LiveData<Event<Unit>>
        get() = _showRemoveCheckedConfirmEvent

    private val _showReminderDialogEvent = MutableLiveData<Event<Long>>()
    val showReminderDialogEvent: LiveData<Event<Long>>
        get() = _showReminderDialogEvent

    private val _showLabelsFragmentEvent = MutableLiveData<Event<Long>>()
    val showLabelsFragmentEvent: LiveData<Event<Long>>
        get() = _showLabelsFragmentEvent

    private val _showLinkDialogEvent = MutableLiveData<Event<String>>()
    val showLinkDialogEvent: LiveData<Event<String>>
        get() = _showLinkDialogEvent

    private val _showColorDialogEvent = MutableLiveData<Event<Int>>()
    val showColorDialogEvent: LiveData<Event<Int>>
        get() = _showColorDialogEvent

    private val _currentNoteColor = MutableLiveData<Int>()
    val currentNoteColor: LiveData<Int>
        get() = _currentNoteColor

    private val _openLinkEvent = MutableLiveData<Event<String>>()
    val openLinkEvent: LiveData<Event<String>>
        get() = _openLinkEvent

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent

    /**
     * Whether to show date item.
     */
    private val shouldShowDate: Boolean
        get() = if (isNewNote) false else prefs.shownDateField != ShownDateField.NONE

    /**
     * Whether note is currently in trash (deleted) or not.
     */
    private val isNoteInTrash: Boolean
        get() = status == NoteStatus.DELETED

    private var updateNoteJob: Job? = null
    private var restoreNoteJob: Job? = null

    init {
        if (KEY_NOTE_ID in savedStateHandle) {
            restoreNoteJob = viewModelScope.launch {
                isNewNote = savedStateHandle[KEY_IS_NEW_NOTE] ?: false

                val note = notesRepository.getNoteById(savedStateHandle[KEY_NOTE_ID] ?: Note.NO_ID)
                if (note != null) {
                    this@EditViewModel.note = note
                    _currentNoteColor.value = note.color
                }
                restoreNoteJob = null
            }
        }
    }

    /**
     * Initialize the view model to edit a note with the ID [noteId].
     * The view model can only be started once to edit a note.
     * Subsequent calls with different arguments will do nothing and previous note will be edited.
     *
     * @param noteId Can be [Note.NO_ID] to create a new note with [type], [title] and [content].
     * @param labelId Can be different from [Label.NO_ID] to initially set a label on a new note.
     * @param changeReminder Whether to start editing note by first changing the reminder.
     */
    fun start(
        noteId: Long = Note.NO_ID,
        labelId: Long = Label.NO_ID,
        changeReminder: Boolean = false,
        type: NoteType = NoteType.TEXT,
        title: String = "",
        content: String = "",
    ) {
        viewModelScope.launch {
            // If fragment was very briefly destroyed then recreated, it's possible that this job is launched
            // before the job to save the note on fragment destruction is called.
            updateNoteJob?.join()
            // Also make sure note is restored after recreation before this is called.
            restoreNoteJob?.join()

            val isFirstStart = (note == BLANK_NOTE)

            // Try to get note by ID with its labels.
            val noteWithLabels = notesRepository.getNoteByIdWithLabels(if (isFirstStart) {
                // first start, use provided note ID
                noteId
            } else {
                // start() was already called, fragment view was probably recreated
                // use the note ID of the note being edited previously
                note.id
            })

            var note = noteWithLabels?.note
            var labels = noteWithLabels?.labels

            if (note == null) {
                // Note doesn't exist, create new blank note of the corresponding type.
                // This is the expected path for creating a new note (by passing Note.NO_ID)
                val date = Date()
                val rank = notesRepository.getNewNoteRank()
                note = BLANK_NOTE.copy(addedDate = date, lastModifiedDate = date, rank = rank,
                    title = title, content = content)
                if (type == NoteType.LIST) {
                    note = note.asListNote()
                }

                val id = notesRepository.insertNote(note)
                note = note.copy(id = id)

                // If a label was passed to be initially set, use it.
                // Otherwise no labels will be set.
                val label = labelsRepository.getLabelById(labelId)
                labels = listOfNotNull(label)
                if (label != null) {
                    labelsRepository.insertLabelRefs(listOf(LabelRef(id, labelId)))
                }

                _noteCreateEvent.send(id)

                isNewNote = true
                savedStateHandle[KEY_IS_NEW_NOTE] = true
            }

            this@EditViewModel.note = note
            this@EditViewModel.labels = labels!!
            status = note.status
            pinned = note.pinned
            reminder = note.reminder
            _currentNoteColor.value = note.color

            savedStateHandle[KEY_NOTE_ID] = note.id

            undoAppendJob = null

            recreateListItems()
            updateEditActionsVisibility()

            var focus: EditFocusChange? = null
            if (isFirstStart && isNewNote) {
                // Focus on title or content initially.
                focus = EditFocusChange(when (prefs.editInitialFocus) {
                    EditInitialFocus.TITLE -> EditFocusLocation.Title
                    EditInitialFocus.CONTENT -> EditFocusLocation.Content
                }, 0, false)

                if (changeReminder) {
                    changeReminder()
                }
            }

            updateListItems(focus)
        }
    }

    /**
     * Update note and save it in database if it was changed.
     * This updates last modified date.
     */
    fun saveNote() {
        // Update note
        updateNote()

        // NonCancellable to avoid save being cancelled if called right before fragment destruction
        updateNoteJob = viewModelScope.launch(NonCancellable) {
            // Compare previously saved note from database with new one.
            // It is possible that note will be null here, if:
            // - Back arrow is clicked, saving note.
            // - Exit is called subsequently, deleting blank note.
            // - onStop calls saveNote again, but note was deleted.
            val oldNote = notesRepository.getNoteById(note.id) ?: return@launch
            if (oldNote != note) {
                // Note was changed.
                // To know whether last modified date should be changed, compare note
                // with a copy that has the original values for fields we don't care about.
                val noteForComparison = note.copy(
                    pinned = if (note.status == oldNote.status) oldNote.pinned else note.pinned)
                if (oldNote != noteForComparison) {
                    note = note.copy(lastModifiedDate = Date())
                }

                notesRepository.updateNote(note)
                updateNoteJob = null
            }
        }
    }

    /**
     * Send exit event. If note is blank, it's discarded.
     */
    fun exit() {
        viewModelScope.launch {
            updateNoteJob?.join()
            if (note.isBlank) {
                // Delete blank note
                deleteNoteInternal()
                _messageEvent.send(EditMessage.BLANK_NOTE_DISCARDED)
            }
            _exitEvent.send()
        }
    }

    private fun updateEditActionsVisibility() {
        val isList = note.type == NoteType.LIST
        val inTrash = isNoteInTrash
        val moreThanOneItem = listItems.asSequence().filterIsInstance<EditItemItem>().count() > 1
        val anyChecked = listItems.asSequence().filterIsInstance<EditItemItem>().any { it.checked }

        val visibility = EditActionsAvailability(
            undo = EditActionAvailability.fromBoolean(!inTrash, undoManager.canUndo),
            redo = EditActionAvailability.fromBoolean(!inTrash, undoManager.canRedo),
            convertToList = EditActionAvailability.fromBoolean(!isList && !inTrash),
            convertToText = EditActionAvailability.fromBoolean(isList && !inTrash),
            reminderAdd = EditActionAvailability.fromBoolean(!inTrash && reminder == null),
            reminderEdit = EditActionAvailability.fromBoolean(!inTrash && reminder != null),
            changeColor = EditActionAvailability.fromBoolean(!inTrash),
            archive = EditActionAvailability.fromBoolean(status == NoteStatus.ACTIVE),
            unarchive = EditActionAvailability.fromBoolean(status == NoteStatus.ARCHIVED),
            delete = EditActionAvailability.fromBoolean(!inTrash),
            deleteForever = EditActionAvailability.fromBoolean(inTrash),
            restore = EditActionAvailability.fromBoolean(inTrash),
            pin = EditActionAvailability.fromBoolean(pinned == PinnedStatus.UNPINNED),
            unpin = EditActionAvailability.fromBoolean(pinned == PinnedStatus.PINNED),
            share = EditActionAvailability.fromBoolean(!inTrash),
            copy = EditActionAvailability.fromBoolean(!inTrash),
            uncheckAll = EditActionAvailability.fromBoolean(isList && anyChecked && !inTrash),
            deleteChecked = EditActionAvailability.fromBoolean(isList && anyChecked && !inTrash),
            sortItems = EditActionAvailability.fromBoolean(isList && moreThanOneItem && !inTrash),
        )
        if (visibility != editActionsAvailability.value) {
            _editActionsAvailability.value = visibility
        }
    }

    fun setMaxUndoEvents(count: Int) {
        undoManager.maxEvents = count
    }

    fun undo() {
        doUndoRedo(undoManager.undo(), ::undoWork)
    }

    private fun undoWork(event: EditEvent) = when (event) {
        is ItemEditEvent -> event.undo(eventPayload)
        is NoteConversionEvent -> {
            note = event.undo(note)
            recreateListItems()
            getNoteConversionFocusChange()
        }
    }

    fun redo() {
        doUndoRedo(undoManager.redo(), ::redoWork)
    }

    private fun redoWork(event: EditEvent) = when (event) {
        is ItemEditEvent -> event.redo(eventPayload)
        is NoteConversionEvent -> {
            note = event.redo(note)
            recreateListItems()
            getNoteConversionFocusChange()
        }
    }

    private fun doUndoRedo(event: EditEvent?, work: (EditEvent) -> EditFocusChange?) {
        undoAppendJob?.cancel()
        undoAppendJob = null

        if (event == null) {
            // Shouldn't happen because buttons are not enabled when not available.
            return
        }

        val focusChange = ignoringTextChanges {
            work(event)
        }
        updateListItems(focusChange)
        updateEditActionsVisibility()
    }

    private fun appendEvent(
        event: EditEvent,
        batch: Boolean = true,
        apply: Boolean = true,
        updateItems: Boolean = true
    ) {
        if (apply) {
            val focusChange = ignoringTextChanges {
                redoWork(event)
            }
            if (updateItems) {
                updateListItems(focusChange)
            }
            updateEditActionsVisibility()
        }

        // Batch all events and stop if inactive for a certain delay.
        undoAppendJob?.cancel()
        if (batch) {
            if (!undoManager.isInBatchMode) {
                undoManager.startBatch()
            }
            undoAppendJob = viewModelScope.launch {
                delay(UNDO_TEXT_DEBOUNCE_DELAY)
                undoManager.endBatch()
            }
        } else {
            undoAppendJob = null
            undoManager.endBatch()
        }

        undoManager.append(event)
        updateEditActionsVisibility()
    }

    fun toggleNoteType() {
        updateNote()

        appendEvent(NoteConversionEvent(
            oldNote = note,
            newNote = when (note.type) {
                // Convert note type
                NoteType.TEXT -> note.asListNote()
                NoteType.LIST -> {
                    if ((note.metadata as ListNoteMetadata).checked.any { it }) {
                        _showRemoveCheckedConfirmEvent.send()
                        return
                    } else {
                        note.asTextNote(keepCheckedItems = true, addBullets = false)
                    }
                }
            },
        ), batch = false)
    }

    private fun getNoteConversionFocusChange(): EditFocusChange? {
        when (note.type) {
            NoteType.TEXT -> {
                // Focus end of content
                val item = findItem<EditContentItem>()
                return EditFocusChange(EditFocusLocation.fromItem(item), item.text.text.length, false)
            }
            NoteType.LIST -> {
                val item = listItems.lastOrNull { it is EditItemItem } as EditItemItem?
                if (item != null) {
                    // Focus end of last item
                    return EditFocusChange(EditFocusLocation.fromItem(item), item.text.text.length, false)
                }
            }
        }
        return null
    }

    fun togglePin() {
        pinned = when (pinned) {
            PinnedStatus.PINNED -> PinnedStatus.UNPINNED
            PinnedStatus.UNPINNED -> PinnedStatus.PINNED
            PinnedStatus.CANT_PIN -> error("Can't pin")
        }
        updateEditActionsVisibility()
    }

    fun changeReminder() {
        _showReminderDialogEvent.send(note.id)
    }

    fun changeLabels() {
        _showLabelsFragmentEvent.send(note.id)
    }

    fun changeColor() {
        _showColorDialogEvent.send(note.color)
    }

    fun onColorChange(color: Int) {
        if (note.color != color) {
            note = note.copy(color = color)
            _currentNoteColor.value = color
            saveNote()
        }
    }

    fun onReminderChange(reminder: Reminder?) {
        this.reminder = reminder
        updateEditActionsVisibility()

        // Update reminder chip
        updateNote()
        recreateListItems()
        updateListItems()
    }

    fun convertToText(keepCheckedItems: Boolean) {
        appendEvent(NoteConversionEvent(
            oldNote = note,
            newNote = note.asTextNote(keepCheckedItems, addBullets = false),
        ), batch = false)
    }

    fun moveNoteAndExit() {
        changeNoteStatusAndExit(if (status == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun restoreNoteAndEdit() {
        note = note.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED)

        status = note.status
        pinned = note.pinned
        updateEditActionsVisibility()

        // Recreate list items so that they are editable.
        recreateListItems()
        updateListItems()

        _messageEvent.send(EditMessage.RESTORED_NOTE)
    }

    fun copyNote(untitledName: String, copySuffix: String) {
        saveNote()

        viewModelScope.launch {
            val newTitle = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix)

            if (!note.isBlank) {
                // If note is blank, don't make a copy, just change the title.
                // Copied blank note should be discarded anyway.
                val date = Date()
                val copy = note.copy(
                    id = Note.NO_ID,
                    title = newTitle,
                    rank = notesRepository.getNewNoteRank(),
                    addedDate = date,
                    lastModifiedDate = date,
                    reminder = null)
                val id = notesRepository.insertNote(copy)
                note = copy.copy(id = id)

                // Set labels for copy
                if (labels.isNotEmpty()) {
                    labelsRepository.insertLabelRefs(createLabelRefs(id))
                }
            }

            // Update title item
            ignoringTextChanges {
                findItem<EditTitleItem>().text.replaceAll(newTitle)
            }
            focusItemAt(EditFocusLocation.Title, newTitle.length)

            undoManager.clear()
            updateEditActionsVisibility()
        }
    }

    fun shareNote() {
        updateNote()
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    fun deleteNote() {
        if (isNoteInTrash) {
            // Delete forever, ask for confirmation.
            _showDeleteConfirmEvent.send()
        } else {
            // Send to trash
            changeNoteStatusAndExit(NoteStatus.DELETED)
        }
    }

    fun deleteNoteForeverAndExit() {
        viewModelScope.launch {
            deleteNoteInternal()
        }
        exit()
    }

    fun uncheckAllItems() {
        val allActualPos = listItems.asSequence()
            .filterIsInstance<EditItemItem>()
            .filter { it.checked }
            .map { it.actualPos }
            .toList()
        appendEvent(ItemCheckEvent(
            actualPos = allActualPos,
            checked = false,
            checkedByUser = false,
        ), batch = false)
    }

    fun deleteCheckedItems() {
        val items = listItems.asSequence()
            .filterIsInstance<EditItemItem>()
            .filter { it.checked }
            .toList()
        // (items is guaranteed to be sorted by actual pos already)
        appendEvent(ItemRemoveEvent(items.map {
            ItemChangeEventItem(it.actualPos, it.text.text.toString(), true)
        }), batch = false)
    }

    fun sortItems() {
        val collator = Collator.getInstance().apply { strength = Collator.TERTIARY }
        val items = listItems.asSequence().filterIsInstance<EditItemItem>()
            .sortedBy { it.actualPos }.map { it.text.text.toString() }.withIndex().toMutableList()
        items.sortWith { a, b -> collator.compare(a.value, b.value) }
        appendEvent(ItemReorderEvent(items.map { it.index }))
    }

    fun focusNoteContent() {
        if (note.type == NoteType.TEXT) {
            val item = findItem<EditContentItem>()
            focusItemAt(EditFocusLocation.Content, item.text.text.length)
        }
    }

    fun openClickedLink() {
        _openLinkEvent.send(linkUrl ?: return)
        linkUrl = null
    }

    private fun changeNoteStatusAndExit(newStatus: NoteStatus) {
        updateNote()

        if (!note.isBlank) {
            // If note is blank, it will be discarded on exit anyway, so don't change it.
            val oldNote = note
            status = newStatus

            pinned = if (status == NoteStatus.ACTIVE) {
                PinnedStatus.UNPINNED
            } else {
                PinnedStatus.CANT_PIN
            }

            updateEditActionsVisibility()

            if (newStatus == NoteStatus.DELETED) {
                // Remove reminder for deleted note
                if (reminder != null) {
                    reminder = null
                    reminderAlarmManager.removeAlarm(note.id)
                }
            }

            saveNote()

            // Show status change message.
            val statusChange = StatusChange(listOf(oldNote), oldNote.status, newStatus)
            _statusChangeEvent.send(statusChange)
        }

        exit()
    }

    /**
     * Update [note] to reflect UI changes, like text changes.
     * Note is not updated in database and last modified date isn't changed.
     */
    private fun updateNote() {
        if (listItems.isEmpty()) {
            // updateNote seems to be called before list items are created due to
            // live data events being called in a non-deterministic order? Return to avoid a crash.
            return
        }

        // Create note
        val title = findItem<EditTitleItem>().text.text.toString()
        val content: String
        val metadata: NoteMetadata
        when (note.type) {
            NoteType.TEXT -> {
                content = findItem<EditContentItem>().text.text.toString()
                metadata = BlankNoteMetadata
            }
            NoteType.LIST -> {
                // Add items in the correct actual order
                val items = MutableList(listItems.count { it is EditItemItem }) {
                    EditItemItem(editableTextProvider.create(""), checked = false, editable = false, actualPos = 0)
                }
                for (item in listItems) {
                    if (item is EditItemItem) {
                        items[item.actualPos] = item
                    }
                }
                content = items.joinToString("\n") { it.text.text }
                metadata = ListNoteMetadata(items.map { it.checked })
            }
        }
        note = note.copy(title = title, content = content,
            metadata = metadata, status = status, pinned = pinned, reminder = reminder)
    }

    private suspend fun deleteNoteInternal() {
        notesRepository.deleteNote(note)
        reminderAlarmManager.removeAlarm(note.id)
    }

    /**
     * Create label refs for a note ID from [labels].
     */
    private fun createLabelRefs(noteId: Long) = labels.map { LabelRef(noteId, it.id) }

    /**
     * Update list items to match content of [note].
     * It's important to make sure [updateNote] was called beforehand so that [note] matches UI content!
     */
    private fun recreateListItems() {
        listItems.clear()
        val canEdit = !isNoteInTrash

        // Date item
        if (shouldShowDate) {
            listItems += EditDateItem(when (prefs.shownDateField) {
                ShownDateField.ADDED -> note.addedDate.time
                ShownDateField.MODIFIED -> note.lastModifiedDate.time
                else -> 0L  // never happens
            })
        }

        // Title item
        listItems += EditTitleItem(editableTextProvider.create(note.title), canEdit)

        when (note.type) {
            NoteType.TEXT -> {
                // Content item
                listItems += EditContentItem(editableTextProvider.create(note.content), canEdit)
            }
            NoteType.LIST -> {
                val noteItems = note.listItems
                if (prefs.moveCheckedToBottom) {
                    // Unchecked list items
                    for ((i, item) in noteItems.withIndex()) {
                        if (!item.checked) {
                            listItems += EditItemItem(editableTextProvider.create(item.content), false, canEdit, i)
                        }
                    }

                    // Item add item
                    if (canEdit) {
                        listItems += EditItemAddItem
                    }

                    // Checked list items
                    val checkCount = noteItems.count { it.checked }
                    if (checkCount > 0) {
                        listItems += EditCheckedHeaderItem(checkCount)
                        for ((i, item) in noteItems.withIndex()) {
                            if (item.checked) {
                                listItems += EditItemItem(editableTextProvider.create(item.content), true, canEdit, i)
                            }
                        }
                    }
                } else {
                    // List items
                    for ((i, item) in noteItems.withIndex()) {
                        listItems += EditItemItem(editableTextProvider.create(item.content), item.checked, canEdit, i)
                    }

                    // Item add item
                    if (canEdit) {
                        listItems += EditItemAddItem
                    }
                }
            }
        }

        val chips = mutableListOf<Any>()
        if (reminder != null) {
            chips += reminder!!
        }
        chips.addAll(labels)
        if (chips.isNotEmpty()) {
            listItems += EditChipsItem(chips)
        }
    }

    private fun updateListItems(focusChange: EditFocusChange? = null) {
        if (focusChange != null) {
            // If focusChange.itemExists is false, we must send the focus event before updating the list.
            // See NoteAdapter for why this is so.
            _focusEvent.send(focusChange)
        }
        _editItems.value = listItems.toMutableList()
    }

    private fun focusItemAt(location: EditFocusLocation, textPos: Int) {
        _focusEvent.send(EditFocusChange(location, textPos, true))
    }

    private fun convertNewlinesToListItems(index: Int, start: Int, oldText: String, newText: String) {
        // User inserted line breaks in list items, split it into multiple items.
        // The item at index will keep the first line of text.
        val item = listItems[index] as EditItemItem
        val itemText = item.text.text
        val textBefore = itemText.substring(0, start) + oldText + itemText.substring(start + newText.length)
        val lines = itemText.split("\n")
        val lastAddedLine = newText.substringAfterLast('\n')
        ignoringTextChanges {
            item.text.replaceAll(textBefore)
        }

        // If this happens in the checked group when moving checked to the bottom, new items will be checked.
        val newChecked = item.checked && prefs.moveCheckedToBottom
        val location = EditFocusLocation.fromItem(item)
        appendEvent(BatchEvent(listOf(
            FocusChangeEvent(before = EditFocusChange(location, start, true)),
            // Note: this event may be empty, that's fine. It will handle the focus change on undo.
            TextChangeEvent.create(
                location = location,
                start = 0,
                end = textBefore.length,
                oldText = textBefore,
                newText = lines.first()
            ),
            ItemAddEvent((1..<lines.size).map { i ->
                ItemChangeEventItem(item.actualPos + i, lines[i], newChecked)
            }),
            // Focus the last added item at the position where the text change ends.
            FocusChangeEvent(after = EditFocusChange(
                EditFocusLocation.Item(item.actualPos + lines.lastIndex),
                lastAddedLine.length, false)),
        )))
    }

    override fun onTextChanged(index: Int, start: Int, end: Int, oldText: String, newText: String) {
        if (ignoreTextChanges) {
            // Currently undoing or redoing something, ignore text changes.
            return
        }

        val item = listItems.getOrNull(index) as? EditTextItem ?: return
        val event = TextChangeEvent.create(EditFocusLocation.fromItem(item), start, end, oldText, newText)
        when (item) {
            is EditTitleItem, is EditContentItem -> appendEvent(event, apply = false)
            is EditItemItem -> {
                if ('\n' in newText) {
                    convertNewlinesToListItems(index, start, oldText, newText)
                } else {
                    appendEvent(event, apply = false)
                }
            }
        }
    }

    private inline fun <R> ignoringTextChanges(work: () -> R): R {
        ignoreTextChanges = true
        val result = work()
        ignoreTextChanges = false
        return result
    }

    override fun onNoteItemCheckChanged(index: Int, checked: Boolean) {
        // First apply event with "checked by user", then add it to undo queue without that flag,
        // because it won't be the case when the event is being undone or redone.
        val event = ItemCheckEvent(
            actualPos = listOf((listItems[index] as EditItemItem).actualPos),
            checked = checked,
            checkedByUser = true,
        )
        event.redo(eventPayload)
        updateListItems()

        appendEvent(event.copy(checkedByUser = false), apply = false)
    }

    override fun onNoteTitleEnterPressed() {
        if (note.type != NoteType.LIST) {
            return
        }

        val noItems = listItems.none { it is EditItemItem }
        val noUncheckedItems = listItems.none { it is EditItemItem && !it.checked }
        if (noItems || prefs.moveCheckedToBottom && noUncheckedItems) {
            // No list items, add one.
            onNoteItemAddClicked()
        }
    }

    private fun focusEndOfTitle() {
        // Backspace in the content, focus the end of the title.
        val item = findItem<EditTitleItem>()
        focusItemAt(EditFocusLocation.Title, item.text.text.length)
    }

    private fun joinListItemsOnBackspace(index: Int) {
        val item = listItems[index] as EditItemItem
        val text = item.text.text.toString()

        val prevItem = listItems[index - 1] as EditItemItem
        val prevText = prevItem.text.text.toString()

        appendEvent(BatchEvent(listOf(
            FocusChangeEvent(before = EditFocusChange.atStartOfItem(item, false)),
            TextChangeEvent.create(
                location = EditFocusLocation.fromItem(prevItem),
                start = 0,
                end = prevText.length,
                oldText = prevText,
                newText = prevText + text,
            ),
            ItemRemoveEvent(listOf(ItemChangeEventItem.fromItem(item))),
            // Set focus on merge boundary.
            FocusChangeEvent(after = EditFocusChange.atEndOfItem(prevItem, true)),
        )))
    }

    override fun onNoteItemBackspacePressed(index: Int) {
        when (listItems[index]) {
            is EditContentItem -> focusEndOfTitle()
            is EditItemItem -> {
                val prevItem = listItems[index - 1]
                when (prevItem) {
                    is EditItemItem -> joinListItemsOnBackspace(index)
                    is EditTitleItem -> focusEndOfTitle()
                    else -> {}
                }
            }
            else -> {}
        }
    }

    override fun onNoteItemDeleteClicked(index: Int) {
        val item = listItems[index] as? EditItemItem ?: return

        val prevItem = listItems[index - 1] as? EditItemItem
        val nextItem = listItems.getOrNull(index + 1) as? EditItemItem
        val focusAfter = (prevItem ?: nextItem)?.let {
            // Account for the fact the deleting the item may shift the focused item actual position.
            val actualPos = if (it.actualPos < item.actualPos) it.actualPos else it.actualPos - 1
            EditFocusChange(EditFocusLocation.Item(actualPos), it.text.text.length, true)
        }

        appendEvent(BatchEvent(listOf(
            FocusChangeEvent(before = EditFocusChange.atEndOfItem(item)),
            ItemRemoveEvent(listOf(ItemChangeEventItem.fromItem(item))),
            FocusChangeEvent(after = focusAfter),
        )))
    }

    override fun onNoteItemAddClicked() {
        val index = findItemIndex<EditItemAddItem>()
        val prevItem = listItems[index - 1] as EditTextItem  // Either title or list item
        // The new item is added last, so the actual pos is the maximum plus one.
        val actualPos = listItems.maxOf { (it as? EditItemItem)?.actualPos ?: -1 } + 1
        appendEvent(BatchEvent(listOf(
            // We have no idea where focus was before. Use the end of last item.
            FocusChangeEvent(before = EditFocusChange.atEndOfItem(prevItem, true)),
            ItemAddEvent(listOf(ItemChangeEventItem(actualPos, "", false))),
            FocusChangeEvent(after = EditFocusChange(EditFocusLocation.Item(actualPos), 0, false)),
        )))
    }

    override fun onNoteLabelClicked() {
        changeLabels()
    }

    override fun onNoteReminderClicked() {
        changeReminder()
    }

    override fun onNoteClickedToEdit() {
        if (isNoteInTrash) {
            // Cannot edit note in trash! Show message suggesting user to restore the note.
            // This is not just for show. Editing note would change its last modified date
            // which would mess up the auto-delete interval in trash.
            _messageEvent.send(EditMessage.CANT_EDIT_IN_TRASH)
        }
    }

    override fun onLinkClickedInNote(linkText: String, linkUrl: String) {
        this.linkUrl = linkUrl
        _showLinkDialogEvent.send(linkText)
    }

    override val isNoteDragEnabled: Boolean
        get() = !isNoteInTrash && listItems.count { it is EditItemItem } > 1

    override fun onNoteItemSwapped(from: Int, to: Int) {
        val fromActualPos = (listItems[from] as EditItemItem).actualPos
        val toActualPos = (listItems[to] as EditItemItem).actualPos
        appendEvent(ItemSwapEvent(fromActualPos, toActualPos), updateItems = false)

        // Don't update live data, adapter was notified of the change already.
        // However the live data value must be updated!
        Collections.swap(_editItems.value!!, from, to)
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    override val moveCheckedToBottom: Boolean
        get() = prefs.moveCheckedToBottom

    override val textSize: Float
        get() = prefs.textSize.toFloat()

    private inline fun <reified T : EditListItem> findItem() =
        (listItems.find { it is T } ?: error("List item not found")) as T

    private inline fun <reified T : EditListItem> findItemIndex() = listItems.indexOfFirst { it is T }

    companion object {
        private val BLANK_NOTE = Note(Note.NO_ID, NoteType.TEXT, "", "",
            BlankNoteMetadata, Date(0), Date(0), FractionalIndex.INITIAL,
            NoteStatus.ACTIVE, 0, PinnedStatus.UNPINNED, null)

        private const val KEY_NOTE_ID = "noteId"
        private const val KEY_IS_NEW_NOTE = "isNewNote"
        private const val KEY_LINK_URL = "linkUrl"

        val UNDO_TEXT_DEBOUNCE_DELAY = 500.milliseconds

        // Should be more than enough
        const val MAX_UNDO_EVENTS = 2048
    }
}
