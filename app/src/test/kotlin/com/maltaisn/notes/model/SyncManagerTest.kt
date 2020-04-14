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
import com.maltaisn.notes.ui.settings.PreferenceHelper
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.hours


class SyncManagerTest {

    private val loginRepo: LoginRepository = mock()
    private val notesRepo: NotesRepository = mock()
    private val prefs: SharedPreferences = mock()

    private val syncManager = SyncManager(mock(), notesRepo, loginRepo, prefs)

    init {
        whenever(loginRepo.isUserSignedIn) doReturn true
        whenever(prefs.getBoolean(eq(PreferenceHelper.SYNC_OVER_WIFI), any())) doReturn false
    }

    @Test
    fun `should not sync notes if not signed in`() = runBlocking {
        whenever(loginRepo.isUserSignedIn) doReturn false
        whenever(prefs.getLong(eq(PreferenceHelper.LAST_SYNC_TIME), any())) doReturn System.currentTimeMillis()
        syncManager.syncNotes(delay = 1.hours)
        verify(notesRepo, never()).syncNotes(true)
    }

    @Test
    fun `should not sync notes if delay not elapsed`() = runBlocking {
        whenever(prefs.getLong(eq(PreferenceHelper.LAST_SYNC_TIME), any())) doReturn System.currentTimeMillis()
        syncManager.syncNotes(delay = 1.hours)
        verify(notesRepo, never()).syncNotes(true)
    }

    @Test
    fun `should sync notes after delay elapsed`() = runBlocking {
        whenever(prefs.getLong(eq(PreferenceHelper.LAST_SYNC_TIME), any())) doReturn
                (System.currentTimeMillis() - 7.hours.toLongMilliseconds())
        syncManager.syncNotes(delay = 1.hours)
        verify(notesRepo).syncNotes(true)
    }

    @Test
    fun `should sync notes if no delay`() = runBlocking {
        whenever(prefs.getLong(eq(PreferenceHelper.LAST_SYNC_TIME), any())) doReturn System.currentTimeMillis()
        syncManager.syncNotes()
        verify(notesRepo).syncNotes(true)
    }

    @Test
    fun `should call onError on sync error`() = runBlocking {
        val e = IOException("Sync failed")
        whenever(notesRepo.syncNotes(any())) doAnswer { throw e }

        var error: Exception? = null
        syncManager.syncNotes {
            error = it
        }
        assertEquals(e, error)
    }

}
