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

package com.maltaisn.notes.ui.edit.undo

import com.maltaisn.notes.ui.edit.TestEditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.e
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TextUndoActionTest {

    private val editableTextProvider = TestEditableTextProvider()

    @Test
    fun `should create action`() {
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
            val action = testAction(oldRange, oldSub, newSub)

            assertEquals(oldText.substring(action.start, action.end), action.oldText)

            // Old and new texts should have no common parts with original
            assertEquals("", action.oldText.commonPrefixWith(action.newText))
            assertEquals("", action.oldText.commonSuffixWith(action.newText))

            // Action should produce expected text
            val textApplied = action.applyOnText(oldText)
            assertEquals(newText, textApplied)
        }
    }

    @Test
    fun `should merge action`() {
        val randomAction = { text: String, rng: Random ->
            val range = randomTextRange(text, rng)
            val newText = text.withReplacedRange(range, randomString(0..10, rng))
            testAction(text.indices, text, newText)
        }
        for (i in 0..1000) {
            val rng = Random(i)
            val text = randomString(0..10, rng)

            // Two actions apply sequentially
            val action0 = randomAction(text, rng)
            val textInter = action0.applyOnText(text)
            val action1 = randomAction(textInter, rng)
            val textSeq = action1.applyOnText(textInter)

            // Should produce the same result as the merged action (if it exists)
            val merged = action0.mergeWith(action1)

            if (action1.end < action0.start || action1.start > action0.start + action0.newText.length) {
                assertNull(merged)
            } else {
                assertNotNull(merged)
                val textMerged = merged.applyOnText(text)
                assertEquals(textMerged, textSeq, "Test $i")
            }
        }
    }

    @Test
    fun `should not merge actions with different item position`() {
        val action0 = TextUndoAction.create(0, 0, 0, "a", "b")
        val action1 = TextUndoAction.create(1, 0, 0, "b", "c")
        assertNull(action0.mergeWith(action1))
    }

    @Test
    fun `should redo and undo correctly`() {
        // Randomly edit text using the redo action, and then undo back to the original.
        val initialText = "original"

        val item = EditContentItem(initialText.e, true)
        val text = item.text.text
        val actions = mutableListOf<TextUndoAction>()

        val rng = Random(1)
        repeat(1000) {
            val range = randomTextRange(text, rng)
            val oldText = text.substring(range)
            val newText = randomString(0..10, rng)
            val action = testAction(range, oldText, newText)

            val focusChange = action.redo(editableTextProvider, mutableListOf(item))
            assertNotNull(focusChange)
            assertContains(0..text.length, focusChange.pos)
            actions += action
        }

        for (action in actions.asReversed()) {
            val focusChange = action.undo(editableTextProvider, mutableListOf(item))
            assertNotNull(focusChange)
            assertContains(0..text.length, focusChange.pos)
        }
        assertEquals(initialText, item.text.text.toString())
    }

    private fun randomString(length: IntRange, random: Random): String {
        return (0..length.random(random)).map { 'a' + (0..<26).random(random) }.joinToString("")
    }

    private fun randomTextRange(text: CharSequence, random: Random): IntRange {
        val start = (0..text.length).random(random)
        val end = (start..text.length).random(random)
        return start..<end
    }

    private fun String.withReplacedRange(range: IntRange, replacement: String): String {
        return substring(0, range.start) + replacement + substring(range.last + 1)
    }

    private fun TextUndoAction.applyOnText(text: String): String {
        return text.withReplacedRange(start..<end, newText)
    }

    private fun testAction(range: IntRange, old: String, new: String) =
        TextUndoAction.create(0, range.first, range.last + 1, old, new)
}
