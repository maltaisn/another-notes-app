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

package com.maltaisn.notes.ui.edit

import com.maltaisn.notes.ui.edit.event.BatchEvent
import com.maltaisn.notes.ui.edit.event.TextChangeEvent
import junit.framework.TestCase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EditUndoManagerTest {

    private val undoManager = EditUndoManager()

    @Test
    fun `should undo and redo`() {
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)
        assertNull(undoManager.undo())
        assertNull(undoManager.redo())

        undoManager.append(EVENT0)
        TestCase.assertTrue(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        undoManager.append(EVENT1)

        assertEquals(EVENT1, undoManager.undo())
        TestCase.assertTrue(undoManager.canUndo)
        TestCase.assertTrue(undoManager.canRedo)

        assertEquals(EVENT0, undoManager.undo())
        assertFalse(undoManager.canUndo)
        assertNull(undoManager.undo())

        assertEquals(EVENT0, undoManager.redo())
        TestCase.assertTrue(undoManager.canRedo)

        assertEquals(EVENT1, undoManager.redo())
        assertFalse(undoManager.canRedo)
        assertNull(undoManager.redo())
    }

    @Test
    fun `should clear events if appended`() {
        undoManager.append(EVENT0)
        undoManager.append(EVENT1)

        undoManager.undo()
        undoManager.undo()
        undoManager.append(EVENT2)

        assertFalse(undoManager.canRedo)
        assertNull(undoManager.redo())
        TestCase.assertTrue(undoManager.canUndo)

        assertEquals(EVENT2, undoManager.undo())
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should batch new events (none)`() {
        undoManager.startBatch()
        TestCase.assertTrue(undoManager.isInBatchMode)
        assertFalse(undoManager.canUndo)
        undoManager.endBatch()
        assertFalse(undoManager.isInBatchMode)
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should batch new events (single merged)`() {
        undoManager.startBatch()

        undoManager.append(EVENT0)
        TestCase.assertTrue(undoManager.canUndo)
        undoManager.append(EVENT1)
        undoManager.endBatch()

        assertEquals(EVENT01, undoManager.undo())
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should batch new events (multiple)`() {
        undoManager.startBatch()
        undoManager.append(EVENT0)
        undoManager.append(EVENT2)
        undoManager.endBatch()

        assertEquals(BatchEvent(listOf(EVENT0, EVENT2)), undoManager.undo())
    }

    @Test
    fun `should batch new events (sub batch)`() {
        undoManager.startBatch()
        undoManager.append(EVENT0)
        undoManager.append(BatchEvent(listOf(EVENT1, EVENT2)))
        undoManager.endBatch()

        assertEquals(BatchEvent(listOf(EVENT0, EVENT1, EVENT2)), undoManager.undo())
    }

    @Test
    fun `should not allow redo after event added to batch`() {
        undoManager.append(EVENT0)
        undoManager.undo()
        TestCase.assertTrue(undoManager.canRedo)

        undoManager.startBatch()
        TestCase.assertTrue(undoManager.canRedo)
        undoManager.append(EVENT1)
        assertFalse(undoManager.canRedo)

        undoManager.endBatch()
        assertFalse(undoManager.canRedo)
    }

    @Test
    fun `should end batch on undo`() {
        undoManager.startBatch()
        undoManager.append(EVENT0)
        assertEquals(EVENT0, undoManager.undo())
        assertFalse(undoManager.isInBatchMode)
    }

    @Test
    fun `should end batch on redo`() {
        undoManager.append(EVENT0)
        undoManager.undo()

        undoManager.startBatch()
        assertEquals(EVENT0, undoManager.redo())
        assertFalse(undoManager.isInBatchMode)
    }

    @Test
    fun `should not do anything if ending unstarted batch mode`() {
        assertFalse(undoManager.isInBatchMode)
        undoManager.endBatch()
        undoManager.endBatch()
        assertFalse(undoManager.isInBatchMode)
    }

    @Test
    fun `should discard old events if too many`() {
        undoManager.maxEvents = 2
        undoManager.append(EVENT0)
        undoManager.append(EVENT1)
        undoManager.append(EVENT2)

        undoManager.undo()
        undoManager.undo()
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should clear all events`() {
        undoManager.append(EVENT0)
        undoManager.startBatch()
        undoManager.append(EVENT1)

        undoManager.clear()
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)
        assertFalse(undoManager.isInBatchMode)
    }

    companion object {
        private val EVENT0 = TextChangeEvent.Companion.create(EditFocusLocation.Title, 0, 1, "a", "b")
        private val EVENT1 = TextChangeEvent.Companion.create(EditFocusLocation.Title, 1, 2, "b", "c")
        private val EVENT01 = TextChangeEvent.Companion.create(EditFocusLocation.Title, 0, 2, "ab", "bc")
        private val EVENT2 = TextChangeEvent.Companion.create(EditFocusLocation.Content, 0, 1, "b", "c")
    }
}