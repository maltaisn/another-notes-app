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

package com.maltaisn.notes.ui.note

import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.entity.ListNoteItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.junit.Test
import kotlin.test.assertEquals


class HighlightHelperTest {

    private val json = Json(JsonConfiguration.Stable)

    @Test
    fun `should highlight all matches in string`() {
        assertEquals(listOf(4..5, 7..8),
                HighlightHelper.findHighlightsInString("hello world", "o"))
    }

    @Test
    fun `should highlight all matches in string up to a max`() {
        assertEquals(listOf(4..5, 7..8, 13..14),
                HighlightHelper.findHighlightsInString("hello world foo", "o", 3))
    }

    @Test
    fun `should highlight longer matches`() {
        assertEquals(listOf(2..10),
                HighlightHelper.findHighlightsInString("hello world", "llo worl"))
    }

    @Test
    fun `should highlight no matches`() {
        assertEquals(emptyList(),
                HighlightHelper.findHighlightsInString("hello world", "z"))
    }

    @Test
    fun `should highlight no matches diacritics`() {
        assertEquals(emptyList(),
                HighlightHelper.findHighlightsInString("hello world", "é"))
    }

    @Test
    fun `should highlight no matches spanning items in list note`() {
        assertEquals(emptyList(),
                HighlightHelper.findHighlightsInString("bar\nfoo bar\nhello\nworld", "bar foo"))
    }

    @Test
    fun `should split and shift list note highlights`() {
        val items = listOf(
                ListNoteItem("bar", false),
                ListNoteItem("foo bar", false),
                ListNoteItem("hello", false),
                ListNoteItem("world", false))
        val note = listNote(json, items = items)
        val highlights = HighlightHelper.findHighlightsInString(note.content, "o")
        assertEquals(listOf(emptyList(), listOf(1..2, 2..3), listOf(4..5), listOf(1..2)),
                HighlightHelper.splitListNoteHighlightsByItem(items, highlights))
    }

}
