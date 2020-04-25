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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.note.HighlightHelper
import com.maltaisn.notes.ui.note.NoteViewModel
import com.maltaisn.notes.ui.note.PlaceholderData
import com.maltaisn.notes.ui.note.adapter.HeaderItem
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteItem
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class SearchViewModel @AssistedInject constructor(
        @Assisted savedStateHandle: SavedStateHandle,
        notesRepository: NotesRepository,
        prefs: PrefsManager
) : NoteViewModel(savedStateHandle, notesRepository, prefs), NoteAdapter.Callback {

    // No need to save this is a saved state handle, SearchView will
    // call query changed listener after it's been recreated.
    private var lastQuery = ""

    private var noteListJob: Job? = null


    init {
        viewModelScope.launch {
            restoreState()
        }
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
            // If a single note is active in selection, treat all as active.
            // Otherwise all notes are archived. Deleted notes are never shown in search.
            if (selectedNotes.isEmpty()) {
                return null
            }
            return if (selectedNotes.any { it.status == NoteStatus.ACTIVE }) {
                NoteStatus.ACTIVE
            } else {
                NoteStatus.ARCHIVED
            }
        }

    override val isNoteSwipeEnabled = false


    private fun createListItems(notes: List<Note>) {
        listItems = buildList {
            var addedArchivedHeader = false
            for (note in notes) {
                // If this is the first archived note, add a header before it.
                if (!addedArchivedHeader && note.status == NoteStatus.ARCHIVED) {
                    this += HeaderItem(ARCHIVED_HEADER_ITEM_ID, R.string.note_location_archived)
                    addedArchivedHeader = true
                }

                val checked = isNoteSelected(note)
                val titleHighlights = HighlightHelper.findHighlightsInString(note.title, lastQuery, 2)
                val contentHighlights = HighlightHelper.findHighlightsInString(note.content, lastQuery, 10)

                this += NoteItem(note.id, note, checked, titleHighlights, contentHighlights)
            }
        }
    }

    override fun updatePlaceholder() = PlaceholderData(
            R.drawable.ic_search, R.string.search_empty_placeholder)


    @AssistedInject.Factory
    interface Factory : AssistedSavedStateViewModelFactory<SearchViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): SearchViewModel
    }

    companion object {
        private const val ARCHIVED_HEADER_ITEM_ID = -1L
    }

}
