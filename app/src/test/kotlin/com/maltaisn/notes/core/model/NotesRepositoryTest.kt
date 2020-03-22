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

import android.content.SharedPreferences
import com.maltaisn.notes.PreferenceHelper
import com.maltaisn.notes.model.ChangeEventsDao
import com.maltaisn.notes.model.NotesDao
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.NotesService
import com.maltaisn.notes.model.entity.*
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import java.util.*


class NotesRepositoryTest {

    private val notesDao: NotesDao = mock()
    private val changesDao: ChangeEventsDao = mock()
    private val notesService: NotesService = mock()

    private val prefsEditor: SharedPreferences.Editor = mock() {
        on { putLong(any(), anyLong()) } doReturn this.mock
    }
    private val prefs: SharedPreferences = mock {
        on { edit() } doReturn prefsEditor
        on { getLong(any(), anyLong()) } doAnswer { it.arguments[1] as Long }
    }

    private val notesRepo = NotesRepository(notesDao, changesDao, notesService, prefs)

    @Test
    fun `should add note in database`() = runBlocking {
        val note = Note(0, "0", NoteType.TEXT, "note",
                "content", null, Date(), Date(), NoteStatus.ACTIVE)
        notesRepo.insertNote(note)
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

    @Test
    fun `should sync notes correctly`() = runBlocking {
        val note1 = Note(0, "0", NoteType.TEXT, "note 1",
                "content 1", null, Date(), Date(), NoteStatus.ACTIVE)
        val note2 = Note(0, "1", NoteType.TEXT, "note 2",
                "content 2", null, Date(), Date(), NoteStatus.ACTIVE)
        whenever(notesDao.getByUuid("0")) doReturn note1
        whenever(changesDao.getAll()) doReturn listOf(ChangeEvent("0", ChangeEventType.UPDATED))

        val newSyncDate = Date()
        whenever(notesService.syncNotes(any())) doReturn NotesService.SyncData(newSyncDate,
                listOf(NotesService.ChangeEventData("1", note2, ChangeEventType.ADDED)))

        notesRepo.syncNotes()

        verify(notesService).syncNotes(NotesService.SyncData(Date(0),
                listOf(NotesService.ChangeEventData("0", note1, ChangeEventType.UPDATED))))
        verify(prefsEditor).putLong(PreferenceHelper.LAST_SYNC_TIME, newSyncDate.time)
        verify(notesDao).getIdByUuid("1")
        verify(notesDao).insert(note2)
        verify(changesDao).clear()
    }

}
