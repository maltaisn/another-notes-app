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

import com.maltaisn.notes.ui.edit.EditFocusLocation
import com.maltaisn.notes.ui.edit.TestEditableTextProvider
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.e
import org.junit.Test
import kotlin.test.assertEquals

class BatchUndoActionTest {

    private val editableTextProvider = TestEditableTextProvider()

    @Test
    fun `should merge action`() {
        val action = BatchUndoAction(listOf(ACTION0))
        val merged = action.mergeWith(ACTION1)
        assertEquals(2, merged.actions.size)
        assertEquals(ACTION0, merged.actions[0])
        assertEquals(ACTION1, merged.actions[1])
    }

    @Test
    fun `should merge batch action`() {
        val action = BatchUndoAction(listOf(ACTION0))
        val merged = action.mergeWith(BatchUndoAction(listOf(ACTION1, ACTION2)))
        assertEquals(3, merged.actions.size)
        assertEquals(ACTION0, merged.actions[0])
        assertEquals(ACTION1, merged.actions[1])
        assertEquals(ACTION2, merged.actions[2])
    }

    @Test
    fun `should undo action`() {
        val item = EditTitleItem("bc".e, true)
        val action = BatchUndoAction(listOf(ACTION0, ACTION1))
        action.undo(UndoPayload(editableTextProvider, mutableListOf(item)))
        assertEquals("ab", item.text.text.toString())
    }

    @Test
    fun `should redo action`() {
        val item = EditTitleItem("ab".e, true)
        val action = BatchUndoAction(listOf(ACTION0, ACTION1))
        action.redo(UndoPayload(editableTextProvider, mutableListOf(item)))
        assertEquals("bc", item.text.text.toString())
    }

    companion object {
        private val ACTION0 = TextUndoAction.create(EditFocusLocation.Title, 0, 1, "a", "b")
        private val ACTION1 = TextUndoAction.create(EditFocusLocation.Title, 1, 2, "b", "c")
        private val ACTION2 = TextUndoAction.create(EditFocusLocation.Content, 0, 1, "b", "c")
    }
}
