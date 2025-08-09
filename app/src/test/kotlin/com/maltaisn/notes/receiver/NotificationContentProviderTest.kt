/*
 * Copyright 2025 Nicolas Maltais
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

package com.maltaisn.notes.receiver

import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.testNote
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class NotificationContentProviderTest {

    private lateinit var provider: NotificationContentProvider
    private lateinit var prefs: PrefsManager

    @Before
    fun before() {
        prefs = mock {
            on { moveCheckedToBottom } doReturn false
        }

        provider = NotificationContentProvider(prefs)
    }

    @Test
    fun `should return content for text note`() = runTest {
        val note = testNote(content = "line 1\nline 2\nline 3\nline4")
        assertEquals(note.content, provider.getContent(note, 0))
        assertEquals(note.content, provider.getContent(note))
    }

    @Test
    fun `should return content for list note`() = runTest {
        val note = listNote(items = listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", false),
            ListNoteItem("item 4", true),
        ))
        assertEquals("☑ item 1\n☐ item 2", provider.getContent(note, 2))
        assertEquals("☑ item 1\n☐ item 2\n☐ item 3\n☑ item 4", provider.getContent(note))
    }

    @Test
    fun `should return content for list note (move checked to bottom)`() = runTest {
        whenever(prefs.moveCheckedToBottom) doReturn true

        val note = listNote(items = listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", false),
            ListNoteItem("item 4", true),
        ))
        assertEquals("☐ item 2\n☐ item 3", provider.getContent(note, 2))
        assertEquals("☐ item 2\n☐ item 3\n☑ item 1\n☑ item 4", provider.getContent(note))
    }
}
