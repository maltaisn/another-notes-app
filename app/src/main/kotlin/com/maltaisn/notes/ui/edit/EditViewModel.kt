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

package com.maltaisn.notes.ui.edit

import android.text.Editable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.*
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.edit.adapter.*
import com.maltaisn.notes.ui.send
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject


class EditViewModel @Inject constructor(
        private val notesRepository: NotesRepository
) : ViewModel(), EditAdapter.Callback {

    private var note: Note = BLANK_NOTE
    private var listItems = mutableListOf<EditListItem>()
        set(value) {
            field = value
            _editItems.value = value
        }

    private val _noteType = MutableLiveData<NoteType?>()
    val noteType: LiveData<NoteType?>
        get() = _noteType

    private val _noteStatus = MutableLiveData<NoteStatus?>()
    val noteStatus: LiveData<NoteStatus?>
        get() = _noteStatus

    private val _editItems = MutableLiveData<MutableList<EditListItem>>()
    val editItems: LiveData<MutableList<EditListItem>>
        get() = _editItems

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

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent

    private val isNoteInTrash: Boolean
        get() = note.status == NoteStatus.TRASHED


    fun start(noteId: Long) {
        this.note = BLANK_NOTE
        viewModelScope.launch {
            // Try to get note by ID.
            var note = notesRepository.getById(noteId)
            if (note == null) {
                // Note doesn't exist, create new blank text note.
                val date = Date()
                note = BLANK_NOTE.copy(uuid = Note.generateNoteUuid(),
                        addedDate = date, lastModifiedDate = date, synced = false)
                val id = notesRepository.insertNote(note)
                note = note.copy(id = id)
            }
            this@EditViewModel.note = note

            _noteType.value = note.type
            _noteStatus.value = note.status

            createListItems()
        }
    }

    fun save() {
        if (isNoteInTrash) {
            // Note can't be edited in trash, no need to save.
            return
        }

        // Create note
        val title = (listItems[0] as EditTitleItem).title.toString()
        val content: String
        val metadata: NoteMetadata
        when (note.type) {
            NoteType.TEXT -> {
                content = (listItems[1] as EditContentItem).content.toString()
                metadata = BlankNoteMetadata
            }
            NoteType.LIST -> {
                val items = listItems.filterIsInstance<EditItemItem>()
                content = items.joinToString("\n") { it.content }
                metadata = ListNoteMetadata(items.map { it.checked })
            }
        }
        note = Note(note.id, note.uuid, note.type, title, content, metadata,
                note.addedDate, note.lastModifiedDate, note.status, false)

        // Update note
        viewModelScope.launch {
            notesRepository.updateNote(note)
        }
    }

    fun exit() {
        if (note.isBlank) {
            // Delete blank note
            viewModelScope.launch {
                notesRepository.deleteNote(note)
                _messageEvent.send(EditMessage.BLANK_NOTE_DISCARDED)
                _exitEvent.send()
            }
        } else {
            _exitEvent.send()
        }
    }

    fun toggleNoteType() {
        save()

        // Convert note type
        val newType = when (note.type) {
            NoteType.TEXT -> NoteType.LIST
            NoteType.LIST -> NoteType.TEXT
        }
        note = note.convertToType(newType)
        _noteType.value = newType

        // Update list items
        createListItems()
    }

    fun moveNoteAndExit() {
        changeNoteStatusAndExit(if (note.status == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun restoreNoteAndEdit() {
        note = note.copy(status = NoteStatus.ACTIVE, lastModifiedDate = Date(), synced = false)

        // Recreate list items so that they are editable.
        createListItems()

        _messageEvent.send(EditMessage.RESTORED_NOTE)
        _noteStatus.value = note.status
    }

    fun copyNote(untitledName: String, copySuffix: String) {
        save()

        viewModelScope.launch {
            val newTitle = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix)
            if (!note.isBlank) {
                // If note is blank, don't make a copy, just change the title.
                // Copied blank note should be discarded anyway.
                val date = Date()
                val copy = note.copy(
                        id = Note.NO_ID,
                        uuid = Note.generateNoteUuid(),
                        title = newTitle,
                        addedDate = date,
                        lastModifiedDate = date,
                        synced = false)
                val id = notesRepository.insertNote(copy)
                this@EditViewModel.note = copy.copy(id = id)
            }

            // Update title item
            val title = (listItems[0] as EditTitleItem).title as Editable
            title.replace(0, title.length, newTitle)
            focusItemAt(0, newTitle.length, true)
        }
    }

    fun shareNote() {
        save()
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    fun deleteNote() {
        if (isNoteInTrash) {
            // Delete forever
            // TODO ask for confirmation
            viewModelScope.launch {
                notesRepository.deleteNote(note)
            }
            exit()

        } else {
            // Send to trash
            changeNoteStatusAndExit(NoteStatus.TRASHED)
        }
    }

    private fun changeNoteStatusAndExit(newStatus: NoteStatus) {
        save()

        if (!note.isBlank) {
            // If note is blank, it will be discarded on exit anyway, so don't change it.
            val oldNote = note
            val oldStatus = note.status
            note = note.copy(status = newStatus,
                    lastModifiedDate = Date(),
                    synced = false)

            // Show status change message.
            val statusChange = StatusChange(listOf(oldNote), oldStatus, newStatus)
            _statusChangeEvent.send(statusChange)
        }

        exit()
    }

    private fun createListItems() {
        val list = mutableListOf<EditListItem>()
        val canEdit = !isNoteInTrash

        // Title item
        val title = EditTitleItem(note.title, canEdit)
        list += title

        when (note.type) {
            NoteType.TEXT -> {
                // Content item
                val content = EditContentItem(note.content, canEdit)
                list += content
            }
            NoteType.LIST -> {
                // List items
                val items = note.listItems
                for (item in items) {
                    list += EditItemItem(item.content, item.checked, canEdit)
                }

                // Item add item
                if (canEdit) {
                    val itemAdd = EditItemAddItem()
                    list += itemAdd
                }
            }
        }

        listItems = list
    }

    override fun onNoteItemChanged(item: EditItemItem, pos: Int, isPaste: Boolean) {
        if ('\n' in item.content) {
            // User inserted line breaks in list items, split it into multiple items.
            val lines = item.content.split('\n')
            changeListItems { list ->
                (item.content as Editable).replace(0, item.content.length, lines.first())
                for (i in 1 until lines.size) {
                    list.add(pos + i, EditItemItem(lines[i], checked = false, editable = true))
                }
            }

            // If text was pasted, set focus at the end of last items pasted.
            // If a single linebreak was inserted, focus on the new item.
            focusItemAt(pos + lines.size - 1, if (isPaste) lines.last().length else 0, false)
        }
    }

    override fun onNoteItemBackspacePressed(item: EditItemItem, pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Previous item is also a note list item. Merge the two items content,
            // and delete the current item.
            val prevLength = prevItem.content.length
            (prevItem.content as Editable).append(item.content)
            changeListItems { it.removeAt(pos) }

            // Set focus on merge boundary.
            focusItemAt(pos - 1, prevLength, true)
        }
    }

    override fun onNoteItemDeleteClicked(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Set focus at the end of previous item.
            focusItemAt(pos - 1, prevItem.content.length, true)
        } else {
            val nextItem = listItems[pos + 1]
            if (nextItem is EditItemItem) {
                // Set focus at the end of next item.
                focusItemAt(pos + 1, nextItem.content.length, true)
            }
        }

        // Delete item in list.
        changeListItems { it.removeAt(pos) }
    }

    override fun onNoteItemAddClicked() {
        val pos = listItems.size - 1
        changeListItems { list ->
            list.add(pos, EditItemItem("", false, true))
        }
        focusItemAt(pos, 0, false)
    }

    override fun onNoteClickedToEdit() {
        if (isNoteInTrash) {
            // Cannot edit note in trash! Show message suggesting user to restore the note.
            // This is not just for fun. Editing note would change its last modified date
            // which would mess up the auto-delete interval in trash.
            _messageEvent.send(EditMessage.CANT_EDIT_IN_TRASH)
        }
    }

    override val isNoteDragEnabled: Boolean
        get() = listItems.size > 3 && !isNoteInTrash

    override fun onNoteItemSwapped(from: Int, to: Int) {
        Collections.swap(listItems, from, to)
    }

    private fun focusItemAt(pos: Int, textPos: Int, itemExists: Boolean) {
        _focusEvent.send(FocusChange(pos, textPos, itemExists))
    }

    private inline fun changeListItems(change: (MutableList<EditListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    data class FocusChange(val itemPos: Int, val pos: Int, val itemExists: Boolean)

    companion object {
        private val BLANK_NOTE = Note(Note.NO_ID, "", NoteType.TEXT, "", "",
                BlankNoteMetadata, Date(0), Date(0), NoteStatus.ACTIVE, true)
    }

}
