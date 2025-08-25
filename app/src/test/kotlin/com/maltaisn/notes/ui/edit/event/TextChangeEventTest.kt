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

package com.maltaisn.notes.ui.edit.event

import com.maltaisn.notes.ui.edit.EditFocusLocation
import com.maltaisn.notes.ui.edit.TestEditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.e
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TextChangeEventTest {

    private val editableTextProvider = TestEditableTextProvider()

    @Test
    fun `should create event`() {
        for (i in 0..1000) {
            val rng = Random(i)
            val oldText = randomString(0..10, rng)
            val range = randomTextRange(oldText, rng)
            val replacement = randomString(0..10, rng)
            val newText = oldText.withReplacedRange(range, replacement)

            // Choose random length for common parts between old and new text
            val commonStart = (0..range.start).random(rng)
            val commonEnd = (0..(oldText.lastIndex - range.last)).random(rng)
            val oldRange = commonStart..(oldText.lastIndex - commonEnd)
            val newRange = commonStart..(newText.lastIndex - commonEnd)

            val oldSub = oldText.substring(oldRange)
            val newSub = newText.substring(newRange)
            val event = testEvent(oldRange, oldSub, newSub)

            assertEquals(oldText.substring(event.start, event.end), event.oldText)

            // Old and new texts should have no common parts with original
            assertEquals("", event.oldText.commonPrefixWith(event.newText))
            assertEquals("", event.oldText.commonSuffixWith(event.newText))

            // event should produce expected text
            val textApplied = event.applyOnText(oldText)
            assertEquals(newText, textApplied)
        }
    }

    @Test
    fun `should merge event`() {
        val randomEvent = { text: String, rng: Random ->
            val range = randomTextRange(text, rng)
            val newText = text.withReplacedRange(range, randomString(0..10, rng))
            testEvent(text.indices, text, newText)
        }
        for (i in 0..1000) {
            val rng = Random(i)
            val text = randomString(0..10, rng)

            // Two events apply sequentially
            val event0 = randomEvent(text, rng)
            val textInter = event0.applyOnText(text)
            val event1 = randomEvent(textInter, rng)
            val textSeq = event1.applyOnText(textInter)

            // Should produce the same result as the merged event (if it exists)
            val merged = event0.mergeWith(event1)

            if (event1.end < event0.start || event1.start > event0.start + event0.newText.length) {
                assertNull(merged)
            } else {
                assertNotNull(merged)
                val textMerged = merged.applyOnText(text)
                assertEquals(textMerged, textSeq, "Test $i")
            }
        }
    }

    @Test
    fun `should not merge events with different item position`() {
        val event0 = TextChangeEvent.create(EditFocusLocation.Title, 0, 0, "a", "b")
        val event1 = TextChangeEvent.create(EditFocusLocation.Content, 0, 0, "b", "c")
        assertNull(event0.mergeWith(event1))
    }

    @Test
    fun `should redo and undo correctly`() {
        // Randomly edit text using the redo event, and then undo back to the original.
        val initialText = "original"

        val item = EditTitleItem(initialText.e, true)
        val text = item.text.text
        val events = mutableListOf<TextChangeEvent>()

        val rng = Random(1)
        repeat(1000) {
            val range = randomTextRange(text, rng)
            val oldText = text.substring(range)
            val newText = randomString(0..10, rng)
            val event = testEvent(range, oldText, newText)

            val focusChange = event.redo(EventPayload(editableTextProvider, mutableListOf(item)))
            assertNotNull(focusChange)
            assertContains(0..text.length, focusChange.textPos)
            events += event
        }

        for (event in events.asReversed()) {
            val focusChange = event.undo(EventPayload(editableTextProvider, mutableListOf(item)))
            assertNotNull(focusChange)
            assertContains(0..text.length, focusChange.textPos)
        }
        assertEquals(initialText, item.text.text.toString())
    }

    private fun randomTextRange(text: CharSequence, random: Random): IntRange {
        val start = (0..text.length).random(random)
        val end = (start..text.length).random(random)
        return start..<end
    }

    private fun String.withReplacedRange(range: IntRange, replacement: String): String {
        return substring(0, range.start) + replacement + substring(range.last + 1)
    }

    private fun TextChangeEvent.applyOnText(text: String): String {
        return text.withReplacedRange(start..<end, newText)
    }

    private fun testEvent(range: IntRange, old: String, new: String) =
        TextChangeEvent.create(EditFocusLocation.Title, range.first, range.last + 1, old, new)
}
