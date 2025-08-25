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
import kotlin.test.assertEquals

class BatchEventTest {

    private val editableTextProvider = TestEditableTextProvider()

    @Test
    fun `should merge event`() {
        val event = BatchEvent(listOf(EVENT0))
        val merged = event.mergeWith(EVENT1)
        assertEquals(2, merged.events.size)
        assertEquals(EVENT0, merged.events[0])
        assertEquals(EVENT1, merged.events[1])
    }

    @Test
    fun `should merge batch event`() {
        val event = BatchEvent(listOf(EVENT0))
        val merged = event.mergeWith(BatchEvent(listOf(EVENT1, EVENT2)))
        assertEquals(3, merged.events.size)
        assertEquals(EVENT0, merged.events[0])
        assertEquals(EVENT1, merged.events[1])
        assertEquals(EVENT2, merged.events[2])
    }

    @Test
    fun `should undo event`() {
        val item = EditTitleItem("bc".e, true)
        val event = BatchEvent(listOf(EVENT0, EVENT1))
        event.undo(EventPayload(editableTextProvider, mutableListOf(item)))
        assertEquals("ab", item.text.text.toString())
    }

    @Test
    fun `should redo event`() {
        val item = EditTitleItem("ab".e, true)
        val event = BatchEvent(listOf(EVENT0, EVENT1))
        event.redo(EventPayload(editableTextProvider, mutableListOf(item)))
        assertEquals("bc", item.text.text.toString())
    }

    companion object {
        private val EVENT0 = TextChangeEvent.create(EditFocusLocation.Title, 0, 1, "a", "b")
        private val EVENT1 = TextChangeEvent.create(EditFocusLocation.Title, 1, 2, "b", "c")
        private val EVENT2 = TextChangeEvent.create(EditFocusLocation.Content, 0, 1, "b", "c")
    }
}
