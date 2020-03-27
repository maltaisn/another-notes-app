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

import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class NoteTest {

    private val json = Json(JsonConfiguration.Stable)

    @Test(expected = IllegalArgumentException::class)
    fun `should fail to create list note without metadata`() {
        testNote(type = NoteType.LIST, metadata = null)
    }

    @Test(expected = IllegalStateException::class)
    fun `should fail to get list items on text note`() {
        val note = testNote(type = NoteType.TEXT)
        note.getListItems(json)
    }

    @Test
    fun `should get list items on list note`() {
        val note = listNote(json, items = listOf(
                ListNoteItem("0", false),
                ListNoteItem("1", true),
                ListNoteItem("2", true)
        ))
        assertEquals(listOf(
                ListNoteItem("0", false),
                ListNoteItem("1", true),
                ListNoteItem("2", true)
        ), note.getListItems(json))
    }

    @Test
    fun `should get no list items on empty list note`() {
        val note = listNote(json, items = emptyList())
        assertEquals(emptyList(), note.getListItems(json))
    }

    @Test
    fun `should check if not is blank`() {
        val note1 = testNote(title = "note")
        val note2 = testNote(content = "content")
        val note3 = testNote(title = "   ", content = "")
        val note4 = testNote(type = NoteType.LIST, title = "",
                content = "  \n   ", metadata = "not blank")

        assertFalse(note1.isBlank)
        assertFalse(note2.isBlank)
        assertTrue(note3.isBlank)
        assertTrue(note4.isBlank)
    }

    @Test
    fun `should convert text note to list`() {
        val date = Date()
        val textNote = testNote(content = "0\n1", added = date, modified = date)
        val listNote = listNote(json, items = listOf(
                ListNoteItem("0", false),
                ListNoteItem("1", false)
        ), added = date, modified = date)
        assertEquals(listNote, textNote.convertToType(NoteType.LIST, json))
    }

    @Test
    fun `should convert text note with bullets to list`() {
        val date = Date()
        val textNote = testNote(content = "- 0\n* 1\n+ 2", added = date, modified = date)
        val listNote = listNote(json, items = listOf(
                ListNoteItem("0", false),
                ListNoteItem("1", false),
                ListNoteItem("2", false)
        ), added = date, modified = date)
        assertEquals(listNote, textNote.convertToType(NoteType.LIST, json))
    }

    @Test
    fun `should convert empty list note with bullet to text`() {
        val date = Date()
        val listNote = listNote(json, items = listOf(
                ListNoteItem("    ", false),
                ListNoteItem("", true)
        ), added = date, modified = date)
        val textNote = testNote(content = "", added = date, modified = date)
        assertEquals(textNote, listNote.convertToType(NoteType.TEXT, json))
    }

    @Test
    fun `should convert list note to text`() {
        val date = Date()
        val listNote = listNote(json, items = listOf(
                ListNoteItem("0", false),
                ListNoteItem("1", false)
        ), added = date, modified = date)
        val textNote = testNote(content = "- 0\n- 1", added = date, modified = date)
        assertEquals(textNote, listNote.convertToType(NoteType.TEXT, json))
    }

}
