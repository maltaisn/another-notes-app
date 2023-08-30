/*
 * Copyright 2023 Nicolas Maltais
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

package com.maltaisn.notes.ui.note

import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.note.adapter.NoteItemList
import com.maltaisn.notes.ui.note.adapter.NoteItemText
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notesshared.listNote
import com.maltaisn.notesshared.testNote
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class NoteItemFactoryTest {

    private lateinit var prefs: PrefsManager
    private lateinit var factory: NoteItemFactory

    @Before
    fun before() {
        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
            on { getMaximumPreviewLines(any()) } doReturn 5
            on { moveCheckedToBottom } doReturn false
        }
        factory = NoteItemFactory(prefs)
    }

    @Test
    fun `should create text note`() {
        val note = testNote(id = 2, title = "title", content = "content")
        val item = factory.createItem(note, emptyList(), checked = false, showMarkAsDone = false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl, "content".hl, false), item)
    }

    @Test
    fun `should create text note, trim title`() {
        val note = testNote(id = 2, title = " \n\t  title \r  ", content = "content")
        val item = factory.createItem(note, emptyList(), checked = false, showMarkAsDone = false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl, "content".hl, false), item)
    }

    @Test
    fun `should create text note, checked`() {
        val note = testNote(id = 2, title = "title", content = "content")
        val item = factory.createItem(note, emptyList(), checked = true, showMarkAsDone = false)
        assertEquals(NoteItemText(2, note, emptyList(), true, "title".hl, "content".hl, false), item)
    }

    @Test
    fun `should create text note, with labels`() {
        val note = testNote(id = 2, title = "title", content = "content")
        val labels = listOf(Label(1, "a"), Label(2, "b"))
        val item = factory.createItem(note, labels, checked = false, showMarkAsDone = false)
        assertEquals(NoteItemText(2, note, labels, false, "title".hl, "content".hl, false), item)
    }

    @Test
    fun `should create text note, show mark as done`() {
        val note = testNote(id = 2, title = "title", content = "content")
        val item = factory.createItem(note, emptyList(), checked = false, showMarkAsDone = true)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl, "content".hl, true), item)
    }

    @Test
    fun `should create text note, query with no highlights (title)`() {
        // FTS might find results that the simple highlight algorithm won't, hence query with no highlights
        val note = testNote(id = 2, title = "haystack néédlè haystack", content = "content")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), checked = false, showMarkAsDone = true)
        assertEquals(NoteItemText(2, note, emptyList(), false, "haystack néédlè haystack".hl,
            "content".hl, true), item)
    }

    @Test
    fun `should create text note, highlight in title`() {
        val note = testNote(id = 2, title = "haystack needle haystack", content = "content")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false,
            Highlighted("haystack needle haystack", listOf(9..15)), "content".hl, false), item)
    }

    @Test
    fun `should create text note, highlight in title (more than maximum)`() {
        val note = testNote(id = 2, title = "needle needle needle", content = "content")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false,
            Highlighted("needle needle needle", listOf(0..6, 7..13)), "content".hl, false), item)
    }

    @Test
    fun `should create text note, highlight in title, ellipsized (list layout)`() {
        val note = testNote(id = 2,
            title = "haystack haystack haystack haystack haystack needle haystack",
            content = "content")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false,
            Highlighted(HighlightHelper.START_ELLIPSIS + "haystack needle haystack", listOf(11..17)),
            "content".hl, false), item)
    }

    @Test
    fun `should create text note, highlight in title, ellipsized (grid layout)`() {
        val note = testNote(id = 2, title = "haystack haystack haystack needle haystack", content = "content")
        factory.query = "needle"
        whenever(prefs.listLayoutMode) doReturn NoteListLayoutMode.GRID
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false,
            Highlighted(HighlightHelper.START_ELLIPSIS + "haystack needle haystack", listOf(11..17)),
            "content".hl, false), item)
    }

    @Test
    fun `should create text note, append note id to title`() {
        val note = testNote(id = 27, title = "title", content = "content")
        factory.appendIdToTitle = true
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(27, note, emptyList(), false,
            "title (27)".hl, "content".hl, false), item)
    }

    @Test
    fun `should create text note, trim content`() {
        val note = testNote(id = 2, title = "title", content = "   \r     content    \n\t          ")
        val item = factory.createItem(note, emptyList(), checked = false, showMarkAsDone = false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl, "content".hl, false), item)
    }

    @Test
    fun `should create text note, highlight in content`() {
        val note = testNote(id = 2, title = "title", content = "haystack haystack needle haystack")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl,
            Highlighted("haystack haystack needle haystack", listOf(18..24)), false), item)
    }

    @Test
    fun `should create text note, highlight in content, ellipsized (list layout)`() {
        val note = testNote(id = 2, title = "title", content = "haystack ".repeat(16) + "needle haystack")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl,
            Highlighted(HighlightHelper.START_ELLIPSIS + "k haystack haystack needle haystack",
                listOf(22..28)), false), item)
    }

    @Test
    fun `should create text note, highlight in content, ellipsized (grid layout)`() {
        val note = testNote(id = 2, title = "title", content = "haystack ".repeat(8) + "needle haystack")
        factory.query = "needle"
        whenever(prefs.listLayoutMode) doReturn NoteListLayoutMode.GRID
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl,
            Highlighted(HighlightHelper.START_ELLIPSIS + "k haystack haystack needle haystack",
                listOf(22..28)), false), item)
    }

    @Test
    fun `should create text note, highlight in content, ellipsized (less preview lines)`() {
        val note =
            testNote(id = 2, title = "title", content = "haystack haystack haystack haystack haystack needle haystack")
        factory.query = "needle"
        whenever(prefs.getMaximumPreviewLines(NoteType.TEXT)) doReturn 2
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl,
            Highlighted(HighlightHelper.START_ELLIPSIS + "k haystack haystack needle haystack",
                listOf(22..28)), false), item)
    }

    @Test
    fun `should create text note, query with no highlights (content)`() {
        val note = testNote(id = 2, title = "title", content = "haystack haystack néédle haystack")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl,
            "haystack haystack néédle haystack".hl, false), item)
    }

    @Test
    fun `should create list note`() {
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", true),
            ListNoteItem("item 3", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 1".hl,
            "item 2".hl,
            "item 3".hl,
        ), listOf(false, true, true), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, trim items`() {
        val note = listNote(listOf(
            ListNoteItem("   item 1", false),
            ListNoteItem("item 2   ", true),
            ListNoteItem("  \t item 3 \r  ", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 1".hl,
            "item 2".hl,
            "item 3".hl,
        ), listOf(false, true, true), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, checked`() {
        val note = listNote(listOf(
            ListNoteItem("item", false),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), true)
        assertEquals(NoteItemList(2, note, emptyList(), true, "title".hl, listOf("item".hl),
            listOf(false), 0, onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, with labels`() {
        val note = listNote(listOf(
            ListNoteItem("item", false),
        ), id = 2, title = "title")
        val labels = listOf(Label(1, "a"), Label(2, "b"))
        val item = factory.createItem(note, labels, false)
        assertEquals(NoteItemList(2, note, labels, false, "title".hl, listOf("item".hl),
            listOf(false), 0, onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create empty list note`() {
        val note = listNote(emptyList(), id = 2, title = "title")
        val labels = listOf(Label(1, "a"), Label(2, "b"))
        val item = factory.createItem(note, labels, false)
        assertEquals(NoteItemList(2, note, labels, false, "title".hl, emptyList(),
            emptyList(), 0, onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, highlight in items`() {
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", true),
            ListNoteItem("item 3", true),
        ), id = 2, title = "title")
        factory.query = "item"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("item 1", listOf(0..4)),
            Highlighted("item 2", listOf(0..4)),
            Highlighted("item 3", listOf(0..4)),
        ), listOf(false, true, true), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, highlight in items (multiple per item)`() {
        val note = listNote(listOf(
            ListNoteItem("item item 1", true),
            ListNoteItem("item 2 item", true),
            ListNoteItem("3 item item", false),
        ), id = 2, title = "title")
        factory.query = "item"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("item item 1", listOf(0..4, 5..9)),
            Highlighted("item 2 item", listOf(0..4, 7..11)),
            Highlighted("3 item item", listOf(2..6, 7..11)),
        ), listOf(true, true, false), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, highlight in items (more than maximum per item)`() {
        val note = listNote(listOf(
            ListNoteItem("item item 1 item item item", false),
        ), id = 2, title = "title")
        factory.query = "item"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("item item 1 item item item", listOf(0..4, 5..9)),
        ), listOf(false), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, highlight in items (more than maximum per note)`() {
        whenever(prefs.getMaximumPreviewLines(NoteType.LIST)) doReturn 6
        val note = listNote(listOf(
            ListNoteItem("item item 1", false),
            ListNoteItem("item item 2", false),
            ListNoteItem("item item 3", false),
            ListNoteItem("item item 4", false),
            ListNoteItem("item item 5", false),
            ListNoteItem("item item 6", false),
        ), id = 2, title = "title")
        factory.query = "item"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("item item 1", listOf(0..4, 5..9)),
            Highlighted("item item 2", listOf(0..4, 5..9)),
            Highlighted("item item 3", listOf(0..4, 5..9)),
            Highlighted("item item 4", listOf(0..4, 5..9)),
            Highlighted("item item 5", listOf(0..4, 5..9)),
            "item item 6".hl,
        ), listOf(false, false, false, false, false, false), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, highlight in items, ellipsized`() {
        val note = listNote(listOf(
            ListNoteItem("haystack haystack needle", false),
            ListNoteItem("haystack haystack haystack needle", true),
            ListNoteItem("haystack haystack haystack haystack needle", true),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("haystack haystack needle", listOf(18..24)),
            Highlighted(HighlightHelper.START_ELLIPSIS + "ack needle", listOf(6..12)),
            Highlighted(HighlightHelper.START_ELLIPSIS + "ack needle", listOf(6..12)),
        ), listOf(false, true, true), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, query with no highlights`() {
        val note = listNote(listOf(
            ListNoteItem("néedlę", false),
            ListNoteItem("nəèdle", true),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "néedlę".hl,
            "nəèdle".hl
        ), listOf(false, true), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, overflow items`() {
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", true),
            ListNoteItem("item 4", false),
            ListNoteItem("item 5", true),
            ListNoteItem("item 6", false),
            ListNoteItem("item 7", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 1".hl,
            "item 2".hl,
            "item 3".hl,
            "item 4".hl,
            "item 5".hl,
        ), listOf(false, false, true, false, true), 2,
            onlyCheckedInOverflow = false, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, overflow items (only checked in overflow)`() {
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", true),
            ListNoteItem("item 4", false),
            ListNoteItem("item 5", true),
            ListNoteItem("item 6", true),
            ListNoteItem("item 7", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 1".hl,
            "item 2".hl,
            "item 3".hl,
            "item 4".hl,
            "item 5".hl,
        ), listOf(false, false, true, false, true), 2,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, move checked to bottom`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", true),
            ListNoteItem("item 4", false),
            ListNoteItem("item 5", false),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 1".hl,
            "item 2".hl,
            "item 4".hl,
            "item 5".hl,
        ), listOf(false, false, false, false), 1,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, move checked to bottom (minimum items threshold)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", true),
            ListNoteItem("item 4", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 2".hl,
            "item 1".hl,
        ), listOf(false, true), 2,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, move checked to bottom (minimum items threshold but single item)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("item 2", false),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 2".hl,
        ), listOf(false), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, move checked to bottom (minimum items threshold but single checked item)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("item 2", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 2".hl,
        ), listOf(true), 0,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note, move checked to bottom (minimum items threshold vs maximum)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        whenever(prefs.getMaximumPreviewLines(NoteType.LIST)) doReturn 1
        val note = listNote(listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", false),
            ListNoteItem("item 3", true),
            ListNoteItem("item 4", true),
        ), id = 2, title = "title")
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "item 2".hl,
        ), listOf(false), 3,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note and prioritize highlights (more highlights than max items)`() {
        val note = listNote(listOf(
            ListNoteItem("haystack 0", false),
            ListNoteItem("needle 1", true),
            ListNoteItem("haystack 2", false),
            ListNoteItem("needle 3", true),
            ListNoteItem("needle 4", false),
            ListNoteItem("haystack 5", true),
            ListNoteItem("haystack 6", false),
            ListNoteItem("needle 7", false),
            ListNoteItem("needle 8", true),
            ListNoteItem("needle 9", false),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("needle 1", listOf(0..6)),
            Highlighted("needle 3", listOf(0..6)),
            Highlighted("needle 4", listOf(0..6)),
            Highlighted("needle 7", listOf(0..6)),
            Highlighted("needle 8", listOf(0..6)),
        ), listOf(true, true, false, false, true), 5,
            onlyCheckedInOverflow = false, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note and prioritize highlights (less highlights than max items)`() {
        val note = listNote(listOf(
            ListNoteItem("haystack 0", false),
            ListNoteItem("needle 1", true),
            ListNoteItem("haystack 2", false),
            ListNoteItem("needle 3", true),
            ListNoteItem("needle 4", false),
            ListNoteItem("haystack 5", true),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "haystack 0".hl,
            Highlighted("needle 1", listOf(0..6)),
            "haystack 2".hl,
            Highlighted("needle 3", listOf(0..6)),
            Highlighted("needle 4", listOf(0..6)),
        ), listOf(false, true, false, true, false), 1,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note and prioritize highlights (move checked to bottom)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("haystack 0", false),
            ListNoteItem("needle 1", true),
            ListNoteItem("haystack 2", true),
            ListNoteItem("needle 3", false),
            ListNoteItem("needle 4", true),
            ListNoteItem("haystack 5", false),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            "haystack 0".hl,
            Highlighted("needle 3", listOf(0..6)),
            "haystack 5".hl,
            Highlighted("needle 1", listOf(0..6)),
            Highlighted("needle 4", listOf(0..6)),
        ), listOf(false, false, false, true, true), 1,
            onlyCheckedInOverflow = true, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note and prioritize highlights (move checked to bottom, overflow)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("haystack 0", true),
            ListNoteItem("needle 1", false),
            ListNoteItem("haystack 2", true),
            ListNoteItem("haystack 3", true),
            ListNoteItem("haystack 4", false),
            ListNoteItem("needle 5", true),
            ListNoteItem("needle 6", false),
            ListNoteItem("haystack 7", false),
            ListNoteItem("needle 8", true),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("needle 1", listOf(0..6)),
            "haystack 4".hl,
            Highlighted("needle 6", listOf(0..6)),
            Highlighted("needle 5", listOf(0..6)),
            Highlighted("needle 8", listOf(0..6)),
        ), listOf(false, false, false, true, true), 4,
            onlyCheckedInOverflow = false, showMarkAsDone = false), item)
    }

    @Test
    fun `should create list note and prioritize highlights (multiple highlights per item)`() {
        whenever(prefs.moveCheckedToBottom) doReturn true
        val note = listNote(listOf(
            ListNoteItem("haystack 0", true),
            ListNoteItem("needle 1 needle", false),
            ListNoteItem("haystack 2", true),
            ListNoteItem("haystack 3", true),
            ListNoteItem("haystack 4", false),
            ListNoteItem("needle needle 5", true),
            ListNoteItem("needle 6", false),
            ListNoteItem("haystack 7", false),
            ListNoteItem("needle 8 needle", true),
        ), id = 2, title = "title")
        factory.query = "needle"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemList(2, note, emptyList(), false, "title".hl, listOf(
            Highlighted("needle 1 needle", listOf(0..6, 9..15)),
            "haystack 4".hl,
            Highlighted("needle 6", listOf(0..6)),
            Highlighted("needle needle 5", listOf(0..6, 7..13)),
            Highlighted("needle 8 needle", listOf(0..6, 9..15)),
        ), listOf(false, false, false, true, true), 4,
            onlyCheckedInOverflow = false, showMarkAsDone = false), item)
    }

    @Test
    fun `should create note and highlight with zero lines preview`() {
        prefs = mock {
            on { listLayoutMode } doReturn NoteListLayoutMode.LIST
            on { getMaximumPreviewLines(any()) } doReturn 0
            on { moveCheckedToBottom } doReturn false
        }
        val note = testNote(id = 2, title = "title", content = "needle")
        factory.query = "need"
        val item = factory.createItem(note, emptyList(), false)
        assertEquals(NoteItemText(2, note, emptyList(), false, "title".hl,
            Highlighted("needle", listOf(0..4)), showMarkAsDone = false), item)
    }

    private val String.hl: Highlighted
        get() = Highlighted(this, emptyList())
}
