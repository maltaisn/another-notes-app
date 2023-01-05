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

package com.maltaisn.notes.model.entity

import com.maltaisn.notesshared.assertNoteEquals
import com.maltaisn.notesshared.dateFor
import com.maltaisn.notesshared.listNote
import com.maltaisn.notesshared.testNote
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteTest {

    @Test
    fun `should fail to create text note with wrong metadata`() {
        assertFailsWith<IllegalArgumentException> {
            testNote(type = NoteType.TEXT, metadata = ListNoteMetadata(emptyList()))
        }
    }

    @Test
    fun `should fail to create list note with wrong metadata`() {
        assertFailsWith<IllegalArgumentException> {
            testNote(type = NoteType.LIST, metadata = BlankNoteMetadata)
        }
    }

    @Test
    fun `should fail to create list item with line break`() {
        assertFailsWith<IllegalArgumentException> {
            ListNoteItem("two\nlines", false)
        }
    }

    @Test
    fun `should fail to get list items on text note`() {
        assertFailsWith<IllegalStateException> {
            val note = testNote(type = NoteType.TEXT)
            note.listItems
        }
    }

    @Test
    fun `should fail to create note with added date after last modified`() {
        assertFailsWith<IllegalArgumentException> {
            testNote(type = NoteType.TEXT,
                added = dateFor("2020-01-01"),
                modified = dateFor("2019-12-31T23:59:59.999Z"))
        }
    }

    @Test
    fun `should fail to create unpinnable active note`() {
        assertFailsWith<IllegalArgumentException> {
            testNote(status = NoteStatus.ACTIVE, pinned = PinnedStatus.CANT_PIN)
        }
    }

    @Test
    fun `should fail to create pinnable archived and deleted note`() {
        assertFailsWith<IllegalArgumentException> {
            testNote(status = NoteStatus.ARCHIVED, pinned = PinnedStatus.PINNED)
        }
        assertFailsWith<IllegalArgumentException> {
            testNote(status = NoteStatus.DELETED, pinned = PinnedStatus.UNPINNED)
        }
    }

    @Test
    fun `should fail to create deleted note with reminder`() {
        assertFailsWith<IllegalArgumentException> {
            testNote(status = NoteStatus.DELETED, reminder = Reminder.create(
                dateFor("2020-07-29"),
                Recurrence.DOES_NOT_REPEAT, RecurrenceFinder()))
        }
    }

    @Test
    fun `should get list items on list note`() {
        val items = listOf(
            ListNoteItem("0", false),
            ListNoteItem("1", true),
            ListNoteItem("2", true))
        val note = listNote(items)
        assertEquals(items, note.listItems)
    }

    @Test
    fun `should get list items on list note (trimmed)`() {
        val items = listOf(
            ListNoteItem("  0 ", false),
            ListNoteItem("1     ", true),
            ListNoteItem("\r\t 2 \r  ", true))
        val note = listNote(items)
        assertEquals(listOf(
            ListNoteItem("0", false),
            ListNoteItem("1", true),
            ListNoteItem("2", true)), note.listItems)
    }

    @Test
    fun `should get no list items on empty list note`() {
        val note = listNote(emptyList())
        assertEquals(emptyList(), note.listItems)
    }

    @Test
    fun `should check if note is blank`() {
        val note1 = testNote(title = "note")
        val note2 = testNote(content = "content")
        val note3 = testNote(title = "   ", content = "")
        val note4 = listNote(listOf(ListNoteItem("  ", true)), title = "  ")
        val note5 = testNote(title = "", content = "", reminder = Reminder(dateFor("2022-01-01"),
            null, dateFor("2022-01-01"), 1, false))

        assertFalse(note1.isBlank)
        assertFalse(note2.isBlank)
        assertTrue(note3.isBlank)
        assertTrue(note4.isBlank)
        assertFalse(note5.isBlank)
    }

    @Test
    fun `should convert text note to list`() {
        val textNote = testNote(content = "0\n1")
        val listNote = listNote(listOf(
            ListNoteItem("0", false),
            ListNoteItem("1", false)))
        assertNoteEquals(listNote, textNote.asListNote(), ignoreId = true)
    }

    @Test
    fun `should convert text note with bullets to list`() {
        val textNote = testNote(content = "- 0\n* 1\n+ 2")
        val listNote = listNote(listOf(
            ListNoteItem("0", false),
            ListNoteItem("1", false),
            ListNoteItem("2", false)))
        assertNoteEquals(listNote, textNote.asListNote(), ignoreId = false)
    }

    @Test
    fun `should convert text note with bullets to list (pre trim)`() {
        val textNote = testNote(content = "\t\n     - 0\n* 1\n+ 2\n\n")
        val items = listOf(
            ListNoteItem("0", false),
            ListNoteItem("1", false),
            ListNoteItem("2", false))
        val listNote = listNote(items)
        val converted = textNote.asListNote()
        assertNoteEquals(listNote, converted, ignoreId = false)
        assertEquals(items, converted.listItems)
    }

    @Test
    fun `should convert empty list note with bullet to text`() {
        val listNote = listNote(listOf(
            ListNoteItem("    ", false),
            ListNoteItem("", true)))
        val textNote = testNote(content = "")
        assertNoteEquals(textNote, listNote.asTextNote(true), ignoreId = false)
    }

    @Test
    fun `should convert list note to text`() {
        val listNote = listNote(listOf(
            ListNoteItem("0", false),
            ListNoteItem("1", false)))
        val textNote = testNote(content = "- 0\n- 1")
        assertNoteEquals(textNote, listNote.asTextNote(true), ignoreId = false)
    }

    @Test
    fun `should convert empty text note to list`() {
        val textNote = testNote(content = "")
        val listNote = listNote(listOf(ListNoteItem("", false)))
        assertNoteEquals(listNote, textNote.asListNote(), ignoreId = false)
    }

    @Test
    fun `should convert blank list note to text`() {
        val listNote = listNote(listOf(
            ListNoteItem("  ", false),
            ListNoteItem("", false),
            ListNoteItem("    ", false)))
        val textNote = testNote(content = "")
        assertNoteEquals(textNote, listNote.asTextNote(true), ignoreId = false)
    }

    @Test
    fun `should convert list note to text removing checked items`() {
        val listNote = listNote(listOf(
            ListNoteItem("item 1", true),
            ListNoteItem("item 2", true),
            ListNoteItem("item 3", false)))
        val textNote = testNote(content = "- item 3")
        assertNoteEquals(textNote, listNote.asTextNote(false), ignoreId = false)
    }

    @Test
    fun `should convert list note to text removing checked items (leaving none)`() {
        val listNote = listNote(listOf(
            ListNoteItem("item 1", true)))
        val textNote = testNote(content = "")
        assertNoteEquals(textNote, listNote.asTextNote(false), ignoreId = false)
    }

    @Test
    fun `should add copy suffix to title`() {
        assertEquals("note - Copy", Note.getCopiedNoteTitle(
            "note", "untitled", "Copy"))
    }

    @Test
    fun `should increment copy number in title`() {
        assertEquals("note - Copy 43", Note.getCopiedNoteTitle(
            "note - Copy 42", "untitled", "Copy"))
    }

    @Test
    fun `should name untitled note copy`() {
        assertEquals("untitled - Copy", Note.getCopiedNoteTitle(
            "  ", "untitled", "Copy"))
    }

    @Test
    fun `should represent text note as text`() {
        val note = testNote(title = "note title", content = "line 1\nline 2")
        assertEquals("note title\nline 1\nline 2", note.asText())
    }

    @Test
    fun `should represent text note with no title as text`() {
        val note = testNote(title = "   ", content = "line 1\nline 2")
        assertEquals("line 1\nline 2", note.asText())
    }

    @Test
    fun `should represent list note as text`() {
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", true)
        ), title = "list title")
        assertEquals("list title\n- item 1\n- item 2", note.asText())
    }

    @Test
    fun `should represent list note as text no title`() {
        val note = listNote(listOf(
            ListNoteItem("item 1", false),
            ListNoteItem("item 2", true)
        ), title = "list title")
        assertEquals("- item 1\n- item 2", note.asText(includeTitle = false))
    }
}
