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

import com.maltaisn.notes.testNote
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*


class SyncNotesRepositoryTest {

    private val notesDao: NotesDao = mock()
    private val deletedNotesDao: DeletedNotesDao = mock()
    private val notesService: NotesService = mock()

    private val prefs: SyncPrefsManager = mock {
        on { lastSyncTime } doReturn 0
    }

    private val syncRepo = SyncRepository(notesDao, deletedNotesDao, notesService, prefs)

    @Test
    fun `should sync notes correctly`() = runBlocking {
        // Local changes: 0 was changed, 1 is unchanged, 2 was deleted.
        // Remote changes: 0 was deleted, 1 was updated.

        val note0 = testNote(uuid = "0", synced = false)
        whenever(deletedNotesDao.getNotSyncedUuids()) doReturn listOf("2")
        whenever(notesDao.getNotSynced()) doReturn listOf(note0)

        val newSyncDate = Date()
        val newNote1 = testNote(uuid = "1")
        whenever(notesService.syncNotes(any())) doReturn NotesService.SyncData(
                newSyncDate, listOf(newNote1), listOf("0"))

        syncRepo.syncNotes(true)

        verify(notesService).syncNotes(NotesService.SyncData(
                Date(0), listOf(note0), listOf("2")))

        verify(notesDao).getIdByUuid("1")
        verify(notesDao).insertAll(listOf(newNote1))
        verify(notesDao).deleteByUuid(listOf("0"))

        verify(prefs).lastSyncTime = newSyncDate.time

        verify(notesDao).setSyncedFlag(true)
        verify(deletedNotesDao).setSyncedFlag(true)
    }

}
