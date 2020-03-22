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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.DebugUtils
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.main.adapter.MessageItem
import com.maltaisn.notes.ui.main.adapter.NoteItem
import com.maltaisn.notes.ui.main.adapter.NoteListItem
import com.maltaisn.notes.ui.main.adapter.NoteListLayoutMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MainViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val prefs: SharedPreferences) : ViewModel() {


    private var noteStatus = NoteStatus.ACTIVE
    private var noteListJob: Job? = null

    val noteItems = MutableLiveData<List<NoteListItem>>()
    val title = MutableLiveData<Int>()
    val listLayoutMode = MutableLiveData<NoteListLayoutMode>()


    init {
        setNoteStatus(noteStatus)

        val layoutModeVal = prefs.getInt(PreferenceHelper.LIST_LAYOUT_MODE,
                NoteListLayoutMode.LIST.value)
        listLayoutMode.value = NoteListLayoutMode.values().find { it.value == layoutModeVal }
    }


    fun setNoteStatus(status: NoteStatus) {
        noteStatus = status
        title.value = when (status) {
            NoteStatus.ACTIVE -> R.string.note_location_active
            NoteStatus.ARCHIVED -> R.string.note_location_archived
            NoteStatus.TRASHED -> R.string.note_location_deleted
        }

        // Cancel previous flow collection
        noteListJob?.cancel()

        // Update note items live data when database flow emits a list.
        noteListJob = viewModelScope.launch {
            notesRepository.getNotesByStatus(status).collect { notes ->
                noteItems.value = buildItemListFromNotes(notes)
            }
        }
    }

    fun toggleListLayoutMode() {
        val mode = when (listLayoutMode.value!!) {
            NoteListLayoutMode.LIST -> NoteListLayoutMode.GRID
            NoteListLayoutMode.GRID -> NoteListLayoutMode.LIST
        }
        listLayoutMode.value = mode
        prefs.edit().putInt(PreferenceHelper.LIST_LAYOUT_MODE, mode.value).apply()
    }

    fun debugAddRandomNote() {
        viewModelScope.launch {
            notesRepository.insertNote(DebugUtils.getRandomNote(noteStatus))
        }
    }

    private fun buildItemListFromNotes(notes: List<Note>): List<NoteListItem> = buildList {
        if (noteStatus == NoteStatus.TRASHED && notes.isNotEmpty()) {
            // If needed, add reminder that notes get auto-deleted when in trash.
            val lastReminder = prefs.getLong(PreferenceHelper.LAST_TRASH_REMIND_TIME, 0)
            if (System.currentTimeMillis() - lastReminder > TRASH_REMINDER_DELAY * 86400000L) {
                this += MessageItem(TRASH_REMINDER_ITEM_ID,
                        R.string.message_trash_reminder,
                        PreferenceHelper.TRASH_AUTO_DELETE_DELAY) {
                    // Update last remind time when user dismisses message.
                    prefs.edit().putLong(PreferenceHelper.LAST_TRASH_REMIND_TIME,
                            System.currentTimeMillis()).apply()
                    setNoteStatus(noteStatus)  // Kinda inefficient
                }
            }
        }

        // Add note items
        for (note in notes) {
            this += NoteItem(note.id, note)
        }
    }

    companion object {
        private const val TRASH_REMINDER_DELAY = 60
        private const val TRASH_REMINDER_ITEM_ID = -1L
    }

}
