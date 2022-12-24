/*
 * Copyright 2022 Nicolas Maltais
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

import org.junit.Test
import kotlin.test.assertEquals

class HighlightHelperTest {

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
    fun `should highlight all matches in string up to a max (max zero)`() {
        assertEquals(emptyList(),
            HighlightHelper.findHighlightsInString("hello world", "o", 0))
    }

    @Test
    fun `should highlight longer matches`() {
        assertEquals(listOf(2..10),
            HighlightHelper.findHighlightsInString("hello world", "llo worl"))
    }

    @Test
    fun `should highlight if case is different`() {
        assertEquals(listOf(3..8),
            HighlightHelper.findHighlightsInString("HeLlO wOrLd", "lo wo"))
    }

    @Test
    fun `should highlight no matches`() {
        assertEquals(emptyList(),
            HighlightHelper.findHighlightsInString("hello world", "z"))
    }

    @Test
    fun `should highlight no matches diacritics`() {
        // FTS would find a match here, but it won't be highlighted!
        assertEquals(emptyList(),
            HighlightHelper.findHighlightsInString("hello world", "é"))
    }

    @Test
    fun `should highlight no matches spanning multiple lines`() {
        assertEquals(emptyList(),
            HighlightHelper.findHighlightsInString("bar\nfoo bar\nhello\nworld", "bar foo"))
    }

    @Test
    fun `should highlight if query is quoted string`() {
        assertEquals(listOf(4..11),
            HighlightHelper.findHighlightsInString("bar foo bar", "\"foo bar\""))
    }

    @Test
    fun `should not ellipsize start of text for highlight`() {
        assertEquals(Highlighted("text", listOf(0..4)),
            HighlightHelper.getStartEllipsizedText("text", mutableListOf(0..4), 10, 5))
    }

    @Test
    fun `should ellipsize start of text for highlight`() {
        assertEquals(Highlighted(HighlightHelper.START_ELLIPSIS + "stack haystack needle haystack",
            listOf(17..23)),
            HighlightHelper.getStartEllipsizedText("haystack haystack haystack needle haystack",
                mutableListOf(27..33), 20, 15))
    }

    @Test
    fun `should ellipsize start of text for highlight (multiple highlights)`() {
        assertEquals(Highlighted(
            HighlightHelper.START_ELLIPSIS + "stack haystack needle needle needle haystack",
            listOf(17..23, 24..30, 31..37)),
            HighlightHelper.getStartEllipsizedText("haystack haystack haystack needle needle needle haystack",
                mutableListOf(27..33, 34..40, 41..47), 20, 15))
    }

    @Test
    fun `should not ellipsize start of text for highlight (distance vs threshold)`() {
        // threshold says there should be ellipsis made, but ellipsis is impossible while respecting distance.
        assertEquals(Highlighted("haystack needle haystack", listOf(10..16)),
            HighlightHelper.getStartEllipsizedText("haystack needle haystack",
                mutableListOf(10..16), 8, 8))
    }

    @Test
    fun `should ellipsize start of text for highlight (trimming before ellipsis)`() {
        assertEquals(Highlighted(HighlightHelper.START_ELLIPSIS + "needle haystack", listOf(2..8)),
            HighlightHelper.getStartEllipsizedText("haystack                 needle haystack",
                mutableListOf(25..31), 20, 10))
    }

    @Test
    fun `should not ellipsize if no highlights`() {
        assertEquals(Highlighted("haystack haystack", emptyList()),
            HighlightHelper.getStartEllipsizedText("haystack haystack",
                mutableListOf(), 8, 4))
    }

    @Test
    fun `should ellipsize at threshold if distance greater than threshold`() {
        assertEquals(Highlighted(HighlightHelper.START_ELLIPSIS + "ack needle haystack", listOf(7..13)),
            HighlightHelper.getStartEllipsizedText("haystack needle haystack",
                mutableListOf(10..16), 5, 8))
    }
}
