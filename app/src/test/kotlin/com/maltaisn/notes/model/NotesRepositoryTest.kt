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

package com.maltaisn.notes.model

import android.content.SharedPreferences
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.DeletedNote
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.settings.PreferenceHelper
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import java.util.*
import kotlin.test.assertEquals


class NotesRepositoryTest {

    private val notesDao: NotesDao = mock()
    private val deletedNotesDao: DeletedNotesDao = mock()
    private val notesService: NotesService = mock()

    private val prefsEditor: SharedPreferences.Editor = mock() {
        on { putLong(any(), anyLong()) } doReturn this.mock
    }
    private val prefs: SharedPreferences = mock {
        on { edit() } doReturn prefsEditor
        on { getLong(any(), anyLong()) } doAnswer { it.arguments[1] as Long }
    }

    private val notesRepo = NotesRepository(notesDao, deletedNotesDao,
            notesService, prefs, Json(JsonConfiguration.Stable))

    @Test
    fun `should delete note in database`() = runBlocking {
        val note = testNote()
        notesRepo.deleteNote(note)
        verify(notesDao).delete(note)
        verify(deletedNotesDao).insert(DeletedNote(0, note.uuid))
    }

    @Test
    fun `should sync notes correctly`() = runBlocking {
        // Local changes: 0 was changed, 1 is unchanged, 2 was deleted.
        // Remote changes: 0 was deleted, 1 was updated.

        val note0 = testNote(uuid = "0", changed = true)
        whenever(deletedNotesDao.getAllUuids()) doReturn listOf("2")
        whenever(notesDao.getChanged()) doReturn listOf(note0)

        val newSyncDate = Date()
        val newNote1 = testNote(uuid = "1")
        whenever(notesService.syncNotes(any())) doReturn NotesService.SyncData(
                newSyncDate, listOf(newNote1), listOf("0"))

        notesRepo.syncNotes(true)

        verify(notesService).syncNotes(NotesService.SyncData(
                Date(0), listOf(note0), listOf("2")))

        verify(notesDao).getIdByUuid("1")
        verify(notesDao).insertAll(listOf(newNote1))
        verify(notesDao).deleteByUuid(listOf("0"))

        verify(prefsEditor).putLong(PreferenceHelper.LAST_SYNC_TIME, newSyncDate.time)
        verify(notesDao).resetChangedFlag()
        verify(deletedNotesDao).clear()
    }

    @Test
    fun `should return all notes data in json`() = runBlocking {
        val date = DateTimeConverter.toDate("2020-01-01T00:00:00.000Z")
        whenever(notesDao.getAll()) doReturn listOf(
                testNote(1, "0", NoteType.TEXT, "note",
                        "content", BlankNoteMetadata, date, date, NoteStatus.ACTIVE))

        val data = notesRepo.getJsonData()

        assertEquals("""{"0":{"uuid":"0","type":0,"title":"note","content":"content",""" +
            """"metadata":"{\"type\":\"blank\"}","added":"2020-01-01T00:00:00.000Z",""" +
            """"modified":"2020-01-01T00:00:00.000Z","status":0}}""", data)
    }

}
