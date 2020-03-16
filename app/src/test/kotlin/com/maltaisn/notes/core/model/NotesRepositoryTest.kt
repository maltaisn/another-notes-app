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

package com.maltaisn.notes.core.model

import com.maltaisn.notes.model.ChangeEventsDao
import com.maltaisn.notes.model.NotesDao
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.*
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*


class NotesRepositoryTest {

    private val notesDao: NotesDao = mock()
    private val changesDao: ChangeEventsDao = mock()

    private val notesRepo = NotesRepository(notesDao, changesDao, mock(), mock())

    @Test
    fun `should add note in database`() = runBlocking {
        val note = Note(0, "0", NoteType.TEXT, "note",
                "content", null, Date(), Date(), NoteStatus.ACTIVE)
        notesRepo.createNote(note)
        verify(notesDao).insert(note)
        verify(changesDao).insert(ChangeEvent(note.uuid, ChangeEventType.ADDED))
    }

    @Test
    fun `should update note in database`() = runBlocking {
        val note = Note(0, "0", NoteType.TEXT, "note",
                "content", null, Date(), Date(), NoteStatus.ACTIVE)
        notesRepo.updateNote(note)
        verify(notesDao).update(note)
        verify(changesDao).insert(ChangeEvent(note.uuid, ChangeEventType.UPDATED))
    }

    @Test
    fun `should delete note in database`() = runBlocking {
        val note = Note(0, "0", NoteType.TEXT, "note",
                "content", null, Date(), Date(), NoteStatus.ACTIVE)
        notesRepo.deleteNote(note)
        verify(notesDao).delete(note)
        verify(changesDao).insert(ChangeEvent(note.uuid, ChangeEventType.DELETED))
    }

}
