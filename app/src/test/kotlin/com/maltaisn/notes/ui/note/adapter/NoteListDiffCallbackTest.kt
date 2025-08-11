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

package com.maltaisn.notes.ui.note.adapter

import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.FractionalIndex
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.note.NoteItemFactory
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Date
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteListDiffCallbackTest {

    lateinit var callback: NoteListDiffCallback
    lateinit var factory: NoteItemFactory

    @Before
    fun before() {
        val prefs = mock<PrefsManager> {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
            on { getMaximumPreviewLines(any()) } doReturn 5
            on { moveCheckedToBottom } doReturn false
        }
        callback = NoteListDiffCallback()
        callback.enableDebug = false
        factory = NoteItemFactory(prefs)
    }

    @Test
    fun `should return false for different header id`() {
        val item0 = HeaderItem(0, 0)
        val item1 = HeaderItem(1, 0)
        assertFalse(callback.areItemsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different message id`() {
        val item0 = MessageItem(0, 0)
        val item1 = MessageItem(1, 0)
        assertFalse(callback.areItemsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note id`() {
        val note0 = testNote(id = 1)
        val note1 = testNote(id = 2)
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areItemsTheSame(item0, item1))
    }

    @Test
    fun `should return true for same header`() {
        val item0 = HeaderItem(10, 13)
        val item1 = HeaderItem(10, 13)
        assertTrue(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return true for same message`() {
        val item0 = MessageItem(2, 27)
        val item1 = MessageItem(2, 27)
        assertTrue(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for header with different title`() {
        val item0 = HeaderItem(0, 0)
        val item1 = HeaderItem(0, 1)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different message`() {
        val item0 = MessageItem(0, 0)
        val item1 = MessageItem(0, 1)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return true for different note (rank)`() {
        val note0 = testNote(id = 1, rank = FractionalIndex.INITIAL)
        val note1 = note0.copy(id = 2, rank = note0.rank.append())
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertTrue(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (status)`() {
        val note0 = testNote(status = NoteStatus.ACTIVE)
        val note1 = testNote(status = NoteStatus.ARCHIVED)
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (title text)`() {
        val note0 = testNote(title = "a")
        val note1 = testNote(title = "b")
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (title highlights)`() {
        val note0 = testNote(title = "abc")
        val note1 = testNote(title = "abc")
        factory.query = "a"
        val item0 = factory.createItem(note0, emptyList(), false)
        factory.query = "b"
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (content text)`() {
        val note0 = testNote(content = "a")
        val note1 = testNote(content = "b")
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (content highlights)`() {
        val note0 = testNote(content = "abc")
        val note1 = testNote(content = "abc")
        factory.query = "a"
        val item0 = factory.createItem(note0, emptyList(), false)
        factory.query = "b"
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (list item text)`() {
        val note0 = listNote(items = listOf(ListNoteItem("a", true)))
        val note1 = listNote(items = listOf(ListNoteItem("abc", true)))
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (list item highlights)`() {
        val note0 = listNote(items = listOf(ListNoteItem("abc", true)))
        val note1 = listNote(items = listOf(ListNoteItem("abc", true)))
        factory.query = "a"
        val item0 = factory.createItem(note0, emptyList(), false)
        factory.query = "b"
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (added date)`() {
        val note0 = testNote(added = Date(1000), modified = Date(2000))
        val note1 = testNote(added = Date(2000), modified = Date(2000))
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (modified date)`() {
        val note0 = testNote(added = Date(0), modified = Date(1000))
        val note1 = testNote(added = Date(0), modified = Date(2000))
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (list item checks)`() {
        val note0 = listNote(items = listOf(ListNoteItem("abc", false)))
        val note1 = listNote(items = listOf(ListNoteItem("abc", true)))
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (reminder)`() {
        val note0 = testNote(reminder = Reminder(Date(1), null, Date(1), 1, false))
        val note1 = testNote(reminder = Reminder(Date(2), null, Date(2), 1, false))
        val item0 = factory.createItem(note0, emptyList(), false)
        val item1 = factory.createItem(note1, emptyList(), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (checked)`() {
        val note = testNote()
        val item0 = factory.createItem(note, emptyList(), false)
        val item1 = factory.createItem(note, emptyList(), true)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (number of labels)`() {
        val note = testNote()
        val item0 = factory.createItem(note, emptyList(), false)
        val item1 = factory.createItem(note, listOf(Label(0, "abc")), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (label name)`() {
        val note = testNote()
        val item0 = factory.createItem(note, listOf(Label(0, "a")), false)
        val item1 = factory.createItem(note, listOf(Label(0, "abc")), false)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }

    @Test
    fun `should return false for different note (show mark as done)`() {
        val note = testNote()
        val item0 = factory.createItem(note, emptyList(), false, showMarkAsDone = false)
        val item1 = factory.createItem(note, emptyList(), false, showMarkAsDone = true)
        assertFalse(callback.areContentsTheSame(item0, item1))
    }
}
