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

package com.maltaisn.notes.model

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteWithLabels
import kotlinx.coroutines.flow.Flow

interface NotesRepository {

    suspend fun insertNote(note: Note): Long

    suspend fun updateNote(note: Note)
    suspend fun updateNotes(notes: List<Note>)

    suspend fun deleteNote(note: Note)
    suspend fun deleteNotes(notes: List<Note>)

    suspend fun getNoteById(id: Long): Note?
    suspend fun getNoteByIdWithLabels(id: Long): NoteWithLabels?

    suspend fun getLastCreatedNote(): Note?

    fun getNotesByStatus(status: NoteStatus): Flow<List<NoteWithLabels>>
    fun getNotesByLabel(labelId: Long): Flow<List<NoteWithLabels>>
    fun getNotesWithReminder(): Flow<List<NoteWithLabels>>
    fun searchNotes(query: String): Flow<List<NoteWithLabels>>

    suspend fun emptyTrash()
    suspend fun deleteOldNotesInTrash()

    suspend fun clearAllData()
}
