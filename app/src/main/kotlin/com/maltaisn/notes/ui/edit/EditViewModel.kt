/*
 * Copyright 2023 Nicolas Maltais
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
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.edit.adapter.EditAdapter
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditChipsItem
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.adapter.EditableText
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.ui.send
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Date

/**
 * View model for the edit note screen.
 */
class EditViewModel @AssistedInject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefs: PrefsManager,
    private val reminderAlarmManager: ReminderAlarmManager,
    @Assisted private val savedStateHandle: SavedStateHandle,
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
     * can change at any time, so it can be associated with a stable ID. This is problematic for item diff callback
     * and animations, which would rely on ID. Instead, the diff callback was made to rely on identity so that
     * add/remove animations take place correctly. The recycler view was also set up to not animate item appearance,
     * so that if the list is completely recreated, no animation will occur despite all items identity being lost.
     *
     * To allow restoring list note items original positions when checking and unchecking (if move to bottom is set),
     * each list note item carries an `actualPos` field which is the item actual position in the list note.
     * This field must be kept up-to-date after all changes to the list!
     */
    private val listItems: MutableList<EditListItem> = mutableListOf()

    private val _noteType = MutableLiveData<NoteType>()
    val noteType: LiveData<NoteType>
        get() = _noteType

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _notePinned = MutableLiveData<PinnedStatus>()
    val notePinned: LiveData<PinnedStatus>
        get() = _notePinned

    private val _noteReminder = MutableLiveData<Reminder?>()
    val noteReminder: LiveData<Reminder?>
        get() = _noteReminder

    private val _editItems = MutableLiveData<MutableList<EditListItem>>()
    val editItems: LiveData<out List<EditListItem>>
        get() = _editItems

    private val _noteCreateEvent = MutableLiveData<Event<Long>>()
    val noteCreateEvent: LiveData<Event<Long>>
        get() = _noteCreateEvent

    private val _focusEvent = MutableLiveData<Event<FocusChange>>()
    val focusEvent: LiveData<Event<FocusChange>>
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
                note = BLANK_NOTE.copy(addedDate = date, lastModifiedDate = date, title = title, content = content)
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

            _noteType.value = note.type
            _noteStatus.value = status
            _notePinned.value = pinned
            _noteReminder.value = reminder

            savedStateHandle[KEY_NOTE_ID] = note.id

            recreateListItems()

            if (isFirstStart && isNewNote) {
                // Focus on title
                focusItemAt(findItemPos<EditTitleItem>(), 0, false)

                if (changeReminder) {
                    changeReminder()
                }
            }
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

    fun toggleNoteType() {
        updateNote()

        // Convert note type
        note = when (note.type) {
            NoteType.TEXT -> note.asListNote()
            NoteType.LIST -> {
                if ((note.metadata as ListNoteMetadata).checked.any { it }) {
                    _showRemoveCheckedConfirmEvent.send()
                    return
                } else {
                    note.asTextNote(true)
                }
            }
        }
        _noteType.value = note.type

        // Update list items
        recreateListItems()

        // Go to first focusable item
        when (note.type) {
            NoteType.TEXT -> {
                val contentPos = listItems.indexOfLast { it is EditContentItem }
                focusItemAt(contentPos, (listItems[contentPos] as EditContentItem).content.text.length, false)
            }
            NoteType.LIST -> {
                val lastItemPos = listItems.indexOfLast { it is EditItemItem }
                focusItemAt(lastItemPos, (listItems[lastItemPos] as EditItemItem).content.text.length, false)
            }
        }
    }

    fun togglePin() {
        pinned = when (pinned) {
            PinnedStatus.PINNED -> PinnedStatus.UNPINNED
            PinnedStatus.UNPINNED -> PinnedStatus.PINNED
            PinnedStatus.CANT_PIN -> error("Can't pin")
        }
        _notePinned.value = pinned
    }

    fun changeReminder() {
        _showReminderDialogEvent.send(note.id)
    }

    fun changeLabels() {
        _showLabelsFragmentEvent.send(note.id)
    }

    fun onReminderChange(reminder: Reminder?) {
        this.reminder = reminder
        _noteReminder.value = reminder

        // Update reminder chip
        updateNote()
        recreateListItems()
    }

    fun convertToText(keepCheckedItems: Boolean) {
        note = note.asTextNote(keepCheckedItems)
        _noteType.value = NoteType.TEXT

        // Update list items (updateNote previously called in toggleNoteType)
        recreateListItems()
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
        _noteStatus.value = status
        _notePinned.value = pinned

        // Recreate list items so that they are editable.
        recreateListItems()

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
            findItem<EditTitleItem>().title.replaceAll(newTitle)
            focusItemAt(findItemPos<EditTitleItem>(), newTitle.length, true)
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
        for ((i, item) in listItems.withIndex()) {
            if (item is EditItemItem && item.checked) {
                // FIXME breaks animation
                listItems[i] = item.copy(checked = false)
            }
        }
        moveCheckedItemsToBottom()
    }

    fun deleteCheckedItems() {
        listItems.removeAll { it is EditItemItem && it.checked }

        // Update actual pos of items by shifting down
        val itemsByActualPos = listItems.asSequence()
            .filterIsInstance<EditItemItem>()
            .sortedBy { it.actualPos }
        var lastActualPos = -1
        for (item in itemsByActualPos) {
            if (item.actualPos != lastActualPos + 1) {
                item.actualPos = lastActualPos + 1
            }
            lastActualPos = item.actualPos
        }

        moveCheckedItemsToBottom()
    }

    fun focusNoteContent() {
        if (note.type == NoteType.TEXT) {
            val contentItemPos = findItemPos<EditContentItem>()
            val contentItem = listItems[contentItemPos] as EditContentItem
            focusItemAt(contentItemPos, contentItem.content.text.length, true)
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
        val title = findItem<EditTitleItem>().title.text.toString()
        val content: String
        val metadata: NoteMetadata
        when (note.type) {
            NoteType.TEXT -> {
                content = findItem<EditContentItem>().content.text.toString()
                metadata = BlankNoteMetadata
            }
            NoteType.LIST -> {
                // Add items in the correct actual order
                val items = MutableList(listItems.count { it is EditItemItem }) { TEMP_ITEM }
                for (item in listItems) {
                    if (item is EditItemItem) {
                        items[item.actualPos] = item
                    }
                }
                content = items.joinToString("\n") { it.content.text }
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
        listItems += EditTitleItem(DefaultEditableText(note.title), canEdit)

        when (note.type) {
            NoteType.TEXT -> {
                // Content item
                listItems += EditContentItem(DefaultEditableText(note.content), canEdit)
            }
            NoteType.LIST -> {
                val noteItems = note.listItems
                if (prefs.moveCheckedToBottom) {
                    // Unchecked list items
                    for ((i, item) in noteItems.withIndex()) {
                        if (!item.checked) {
                            listItems += EditItemItem(DefaultEditableText(item.content), false, canEdit, i)
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
                                listItems += EditItemItem(DefaultEditableText(item.content), true, canEdit, i)
                            }
                        }
                    }
                } else {
                    // List items
                    for ((i, item) in noteItems.withIndex()) {
                        listItems += EditItemItem(DefaultEditableText(item.content), item.checked, canEdit, i)
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

        updateListItems()
    }

    private fun updateListItems() {
        _editItems.value = listItems.toMutableList()
    }

    override fun onNoteItemChanged(pos: Int, isPaste: Boolean) {
        val item = listItems[pos] as EditItemItem
        if ('\n' in item.content.text) {
            // User inserted line breaks in list items, split it into multiple items.
            // If this happens in the checked group when moving checked to the bottom, new items will be checked.
            val lines = item.content.text.split('\n')
            item.content.replaceAll(lines.first())
            for (listItem in listItems) {
                if (listItem is EditItemItem && listItem.actualPos > item.actualPos) {
                    listItem.actualPos += lines.size - 1
                }
            }
            for (i in 1 until lines.size) {
                listItems.add(pos + i, EditItemItem(DefaultEditableText(lines[i]),
                    checked = item.checked && moveCheckedToBottom, editable = true, item.actualPos + i))
            }

            moveCheckedItemsToBottom() // just to update checked count
            updateListItems()

            // If text was pasted, set focus at the end of last items pasted.
            // If a single linebreak was inserted, focus on the new item.
            focusItemAt(pos + lines.size - 1, if (isPaste) lines.last().length else 0, false)
        }
    }

    override fun onNoteItemCheckChanged(pos: Int, checked: Boolean) {
        val item = listItems[pos] as EditItemItem
        if (item.checked != checked) {
            item.checked = checked
            moveCheckedItemsToBottom()
        }
    }

    override fun onNoteItemBackspacePressed(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Previous item is also a note list item. Merge the two items content,
            // and delete the current item.
            val prevText = prevItem.content
            val prevLength = prevText.text.length
            prevText.append((listItems[pos] as EditItemItem).content.text)
            deleteListItemAt(pos)

            // Set focus on merge boundary.
            focusItemAt(pos - 1, prevLength, true)
        }
    }

    override fun onNoteItemDeleteClicked(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Set focus at the end of previous item.
            focusItemAt(pos - 1, prevItem.content.text.length, true)
        } else {
            val nextItem = listItems.getOrNull(pos + 1)
            if (nextItem is EditItemItem) {
                // Set focus at the end of next item.
                focusItemAt(pos + 1, nextItem.content.text.length, true)
            }
        }

        deleteListItemAt(pos)
    }

    override fun onNoteItemAddClicked(pos: Int) {
        // pos is the position of EditItemAdd item, which is also the position to insert the new item.
        // The new item is added last, so the actual pos is the maximum plus one.
        val actualPos = listItems.maxOf { (it as? EditItemItem)?.actualPos ?: -1 } + 1
        listItems.add(pos, EditItemItem(DefaultEditableText(), checked = false, editable = true, actualPos))
        updateListItems()
        focusItemAt(pos, 0, false)
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
        // Swap items actual positions in list note
        val fromItem = listItems[from] as EditItemItem
        val toItem = listItems[to] as EditItemItem
        val actualPosTemp = fromItem.actualPos
        fromItem.actualPos = toItem.actualPos
        toItem.actualPos = actualPosTemp

        // Don't update live data, adapter was notified of the change already.
        // However the live data value must be updated!
        Collections.swap(listItems, from, to)
        Collections.swap(_editItems.value!!, from, to)
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    override val moveCheckedToBottom: Boolean
        get() = prefs.moveCheckedToBottom

    private fun focusItemAt(pos: Int, textPos: Int, itemExists: Boolean) {
        _focusEvent.send(FocusChange(pos, textPos, itemExists))
    }

    private fun deleteListItemAt(pos: Int) {
        val listItem = listItems[pos] as EditItemItem
        listItems.removeAt(pos)
        // Shift the actual pos of all items after this one
        for (item in listItems) {
            if (item is EditItemItem && item.actualPos > listItem.actualPos) {
                item.actualPos--
            }
        }
        // Update checked/unchecked sections in cast this was the only checked item
        moveCheckedItemsToBottom()
    }

    /**
     * If configured so, move checked items to a separate section at the bottom,
     * and update the checked header count. If no items are checked, the section is removed.
     * Always calls [updateListItems].
     */
    private fun moveCheckedItemsToBottom() {
        if (prefs.moveCheckedToBottom) {
            // Remove the whole checked group
            val checkedItems = listItems.asSequence().filterIsInstance<EditItemItem>()
                .filter { it.checked }.toMutableList()
            listItems.removeAll(checkedItems)
            listItems.removeAll { it is EditCheckedHeaderItem }
            listItems.remove(EditItemAddItem)

            // Sort unchecked items by actual pos
            var lastUncheckedPos = listItems.indexOfLast { it is EditItemItem }
            if (lastUncheckedPos != -1) {
                lastUncheckedPos++
                val firstUncheckedPos = listItems.indexOfFirst { it is EditItemItem }
                listItems.subList(firstUncheckedPos, lastUncheckedPos).sortBy { (it as EditItemItem).actualPos }
            } else {
                lastUncheckedPos = findItemPos<EditTitleItem>() + 1
            }

            // Re-add the checked group if any checked items, items sorted by actual pos
            var pos = lastUncheckedPos
            listItems.add(pos, EditItemAddItem)
            pos++
            if (checkedItems.isNotEmpty()) {
                listItems.add(pos, EditCheckedHeaderItem(checkedItems.size))
                pos++
                checkedItems.sortBy { it.actualPos }
                for (item in checkedItems) {
                    listItems.add(pos, item)
                    pos++
                }
            }
        }
        updateListItems()
    }

    private inline fun <reified T : EditListItem> findItem(): T {
        return (listItems.find { it is T } ?: error("List item not found")) as T
    }

    private inline fun <reified T : EditListItem> findItemPos(): Int {
        return listItems.indexOfFirst { it is T }
    }

    data class FocusChange(val itemPos: Int, val pos: Int, val itemExists: Boolean)

    /**
     * The default class used for editable item text, backed by StringBuilder.
     * When items are bound by the adapter, this is changed to AndroidEditableText instead.
     * The default implementation is only used temporarily (before item is bound) and for testing.
     */
    class DefaultEditableText(text: CharSequence = "") : EditableText {
        override val text = StringBuilder(text)

        override fun append(text: CharSequence) {
            this.text.append(text)
        }

        override fun replaceAll(text: CharSequence) {
            this.text.replace(0, this.text.length, text.toString())
        }

        override fun equals(other: Any?) = (other is DefaultEditableText &&
                other.text.toString() == text.toString())

        override fun hashCode() = text.hashCode()

        override fun toString() = text.toString()
    }

    @AssistedFactory
    interface Factory : AssistedSavedStateViewModelFactory<EditViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): EditViewModel
    }

    companion object {
        private val BLANK_NOTE = Note(Note.NO_ID, NoteType.TEXT, "", "",
            BlankNoteMetadata, Date(0), Date(0), NoteStatus.ACTIVE, PinnedStatus.UNPINNED, null)

        private const val KEY_NOTE_ID = "noteId"
        private const val KEY_IS_NEW_NOTE = "isNewNote"
        private const val KEY_LINK_URL = "linkUrl"

        private val TEMP_ITEM = EditItemItem(DefaultEditableText(), checked = false, editable = false, actualPos = 0)
    }
}
