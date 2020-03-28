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
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.edit.adapter.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject


class EditViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val json: Json) : ViewModel(), EditAdapter.Callback {

    private var note: Note? = null
    private var listItems = mutableListOf<EditListItem>()
        set(value) {
            field = value
            _editItems.value = value
        }

    private var titleItem: EditTitleItem? = null
    private var contentItem: EditContentItem? = null
    private var itemAddItem: EditItemAddItem? = null

    private var deleteOnExit = false

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

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent


    fun start(noteId: Long) {
        this.note = null
        viewModelScope.launch {
            // Try to get note by ID.
            var note = notesRepository.getById(noteId)
            if (note == null) {
                // Note doesn't exist, create new blank text note.
                val date = Date()
                note = Note(Note.NO_ID, generateNoteUuid(), NoteType.TEXT,
                        "", "", null, date, date, NoteStatus.ACTIVE)
                val id = notesRepository.insertNote(note)
                note = note.copy(id = id)
            }
            this@EditViewModel.note = note

            _noteType.value = note.type
            _noteStatus.value = note.status

            createListItems()
        }
    }

    fun save(exit: Boolean) {
        val note = buildNote() ?: return
        viewModelScope.launch {
            if (deleteOnExit || note.isBlank) {
                // Discard blank note.
                notesRepository.deleteNote(note)
            } else {
                // Update note
                notesRepository.updateNote(note)
            }
            if (exit) {
                _exitEvent.value = Event(Unit)
            }
        }
    }

    fun toggleNoteType() {
        // Build note first to reflect any changes.
        val note = buildNote() ?: return

        // Convert note type
        val newType = when (note.type) {
            NoteType.TEXT -> NoteType.LIST
            NoteType.LIST -> NoteType.TEXT
        }
        _noteType.value = newType
        this.note = note.convertToType(newType, json)

        // Update list items
        createListItems()
    }

    fun moveNote() {
        val note = note ?: return
        changeNoteStatus(if (note.status == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
        save(true)
    }

    fun copyNote() {
        val note = note ?: return

        save(false)

        viewModelScope.launch {
            val date = Date()
            val copy = note.copy(
                    id = Note.NO_ID,
                    uuid = generateNoteUuid(),
                    title = note.title + " - Copy",  // TODO localize this
                    addedDate = date,
                    lastModifiedDate = date)
            val id = notesRepository.insertNote(copy)
            this@EditViewModel.note = copy.copy(id = id)

            // Update title item
            val title = titleItem?.title as Editable
            title.replace(0, title.length, copy.title)
        }
    }

    fun shareNote() {
        // TODO start share intent
    }

    fun deleteNote() {
        if (noteStatus.value == NoteStatus.TRASHED) {
            // Delete forever
            deleteOnExit = true
            // TODO ask for confirmation

        } else {
            // Send to trash
            changeNoteStatus(NoteStatus.TRASHED)
        }
        save(true)
    }

    private fun changeNoteStatus(newStatus: NoteStatus) {
        val note = note ?: return
        val newNote = note.copy(status = newStatus, lastModifiedDate = Date())
        this.note = newNote
        _statusChangeEvent.value = Event(StatusChange(listOf(newNote),
                listOf(note.lastModifiedDate), note.status, newStatus))
    }

    private fun generateNoteUuid() = UUID.randomUUID().toString().replace("-", "")


    private fun createListItems() {
        val note = note ?: return

        val list = mutableListOf<EditListItem>()

        // Title item
        val title = titleItem ?: EditTitleItem("")
        title.title = note.title
        titleItem = title
        list += title

        when (note.type) {
            NoteType.TEXT -> {
                // Content item
                val content = contentItem ?: EditContentItem("")
                content.content = note.content
                contentItem = content
                list += content
            }
            NoteType.LIST -> {
                // List items
                val items = note.getListItems(json)
                for (item in items) {
                    list += EditItemItem(item.content, item.checked)
                }

                // Item add item
                val itemAdd = itemAddItem ?: EditItemAddItem()
                itemAddItem = itemAdd
                list += itemAdd
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
                    list.add(pos + i, EditItemItem(lines[i], false))
                }
            }

            // If text was pasted, set focus at the end of last items pasted.
            // If a single linebreak was inserted, focus on the new item.
            _focusEvent.value = Event(FocusChange(pos + lines.size - 1,
                    if (isPaste) lines.last().length else 0, false))
        }
    }

    override fun onNoteItemBackspacePressed(item: EditItemItem, pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Previous item is also a note list item. Merge the two items content,
            // and delete the current item.
            val prevLength = prevItem.content.length
            (prevItem.content as Editable).append(item.content)
            deleteNoteItem(pos)

            // Set focus on merge boundary.
            _focusEvent.value = Event(FocusChange(pos - 1, prevLength, true))
        }
    }

    override fun onNoteItemDeleteClicked(pos: Int) {
        deleteNoteItem(pos)
    }

    override fun onNoteItemAddClicked() {
        changeListItems { list ->
            list.add(listItems.size - 1, EditItemItem("", false))
        }
    }

    private fun deleteNoteItem(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Set focus at the end of previous item.
            _focusEvent.value = Event(FocusChange(pos - 1,
                    prevItem.content.length, true))
        }

        // Delete item in list.
        changeListItems { it.removeAt(pos) }
    }

    private inline fun changeListItems(change: (MutableList<EditListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildNote(): Note? {
        val note = note ?: return null

        val title = titleItem!!.title.toString()
        val content: String
        val metadata: String?
        when (note.type) {
            NoteType.TEXT -> {
                content = contentItem!!.content.toString()
                metadata = null
            }
            NoteType.LIST -> {
                val items = listItems.subList(1, listItems.size - 1) as List<EditItemItem>
                content = items.joinToString("\n") { it.content }
                metadata = json.stringify(ListNoteMetadata.serializer(),
                        ListNoteMetadata(items.map { it.checked }))
            }
        }

        return Note(note.id, note.uuid, note.type, title, content, metadata,
                note.addedDate, note.lastModifiedDate, note.status)
    }

    data class FocusChange(val itemPos: Int, val pos: Int, val itemExists: Boolean)

}
