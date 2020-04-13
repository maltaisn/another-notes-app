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

package com.maltaisn.notes.ui.search

import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.note.HighlightHelper
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.adapter.HeaderItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.settings.PreferenceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject


class SearchViewModel @Inject constructor(
        notesRepository: NotesRepository,
        prefs: SharedPreferences
) : NoteViewModel(notesRepository, prefs), NoteAdapter.Callback {

    private var lastQuery = ""
    private var noteListJob: Job? = null

    init {
        val layoutModeVal = prefs.getInt(PreferenceHelper.LIST_LAYOUT_MODE,
                NoteListLayoutMode.LIST.value)
        _listLayoutMode.value = NoteListLayoutMode.values().find { it.value == layoutModeVal }
    }

    fun searchNotes(query: String) {
        lastQuery = query

        // Cancel previous flow collection / debounce
        noteListJob?.cancel()

        // Update note items live data when database flow emits a list.
        val cleanedQuery = SearchQueryCleaner.clean(query)
        noteListJob = viewModelScope.launch {
            delay(100)
            notesRepository.searchNotes(cleanedQuery).collect { notes ->
                createListItems(notes)
            }
        }
    }

    override val selectedNoteStatus: NoteStatus?
        get() {
            // If a single note is active, treat all as active.
            // Otherwise all notes are archived. Deleted notes are never shown in search.
            if (selectedNotes.isEmpty()) {
                return null
            }
            for (note in selectedNotes) {
                if (note.status == NoteStatus.ACTIVE) {
                    return NoteStatus.ACTIVE
                }
            }
            return NoteStatus.ARCHIVED
        }

    override val isNoteSwipeEnabled = false


    private fun createListItems(notes: List<Note>) {
        listItems = buildList {
            var addedArchivedHeader = false
            for (note in notes) {
                val checked = selectedNotes.any { it.id == note.id }
                if (!addedArchivedHeader && note.status == NoteStatus.ARCHIVED) {
                    this += HeaderItem(-1, R.string.note_location_archived)
                    addedArchivedHeader = true
                }

                val titleHighlights = HighlightHelper.findHighlightsInString(note.title, lastQuery, 2)
                val contentHighlights = HighlightHelper.findHighlightsInString(note.content, lastQuery, 10)

                this += NoteItem(note.id, note, checked, titleHighlights, contentHighlights)
            }
        }
    }

}
