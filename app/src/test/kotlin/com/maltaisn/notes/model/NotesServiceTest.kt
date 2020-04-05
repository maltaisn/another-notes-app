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

import com.google.firebase.auth.FirebaseAuth
import com.maltaisn.notes.model.NotesService.ChangeEventData
import com.maltaisn.notes.model.NotesService.SyncData
import com.maltaisn.notes.model.entity.ChangeEventType
import com.maltaisn.notes.testNote
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import java.util.*
import kotlin.test.assertEquals


class NotesServiceTest {

    @Test(expected = IOException::class)
    fun `should fail unauthenticated`() {
        val service = NotesService(mock(), mock())
        runBlocking {
            service.syncNotes(SyncData(Date(), emptyList()))
        }
    }

    @Test
    fun `should encode and decode sync data structures`() = runBlocking {
        // Note: ID is not serialized so ID 0 (the default) is used everywhere.
        val note1 = testNote()
        val note2 = testNote()
        val syncData = SyncData(Date(), listOf(
                ChangeEventData("1", note1, ChangeEventType.ADDED),
                ChangeEventData("2", note2, ChangeEventType.UPDATED),
                ChangeEventData("3", null, ChangeEventType.DELETED)
        ))

        // Mock auth so user appears authenticated
        val fbAuth = mock<FirebaseAuth> {
            on { currentUser } doReturn mock()
        }

        // "Mock" service so that sync just returns the same data it was passed.
        val service = object : NotesService(fbAuth, mock()) {
            override suspend fun callSyncFunction(data: Any?) = data
        }

        assertEquals(syncData, service.syncNotes(syncData))
    }

}
