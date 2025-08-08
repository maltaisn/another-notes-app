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

import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UndoManagerTest {

    private val undoManager = UndoManager()

    @Test
    fun `should undo and redo`() {
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)
        assertNull(undoManager.undo())
        assertNull(undoManager.redo())

        undoManager.append(ACTION0)
        assertTrue(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        undoManager.append(ACTION1)

        assertEquals(ACTION1, undoManager.undo())
        assertTrue(undoManager.canUndo)
        assertTrue(undoManager.canRedo)

        assertEquals(ACTION0, undoManager.undo())
        assertFalse(undoManager.canUndo)
        assertNull(undoManager.undo())

        assertEquals(ACTION0, undoManager.redo())
        assertTrue(undoManager.canRedo)

        assertEquals(ACTION1, undoManager.redo())
        assertFalse(undoManager.canRedo)
        assertNull(undoManager.redo())
    }

    @Test
    fun `should clear actions if appended`() {
        undoManager.append(ACTION0)
        undoManager.append(ACTION1)

        undoManager.undo()
        undoManager.undo()
        undoManager.append(ACTION2)

        assertFalse(undoManager.canRedo)
        assertNull(undoManager.redo())
        assertTrue(undoManager.canUndo)

        assertEquals(ACTION2, undoManager.undo())
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should batch new actions (none)`() {
        undoManager.startBatch()
        assertTrue(undoManager.isInBatchMode)
        assertFalse(undoManager.canUndo)
        undoManager.endBatch()
        assertFalse(undoManager.isInBatchMode)
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should batch new actions (single merged)`() {
        undoManager.startBatch()

        undoManager.append(ACTION0)
        assertTrue(undoManager.canUndo)
        undoManager.append(ACTION1)
        undoManager.endBatch()

        assertEquals(ACTION01, undoManager.undo())
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should batch new actions (multiple)`() {
        undoManager.startBatch()
        undoManager.append(ACTION0)
        undoManager.append(ACTION2)
        undoManager.endBatch()

        assertEquals(BatchUndoAction(listOf(ACTION0, ACTION2)), undoManager.undo())
    }

    @Test
    fun `should batch new actions (sub batch)`() {
        undoManager.startBatch()
        undoManager.append(ACTION0)
        undoManager.append(BatchUndoAction(listOf(ACTION1, ACTION2)))
        undoManager.endBatch()

        assertEquals(BatchUndoAction(listOf(ACTION0, ACTION1, ACTION2)), undoManager.undo())
    }

    @Test
    fun `should not allow redo after action added to batch`() {
        undoManager.append(ACTION0)
        undoManager.undo()
        assertTrue(undoManager.canRedo)

        undoManager.startBatch()
        assertTrue(undoManager.canRedo)
        undoManager.append(ACTION1)
        assertFalse(undoManager.canRedo)

        undoManager.endBatch()
        assertFalse(undoManager.canRedo)
    }

    @Test
    fun `should end batch on undo`() {
        undoManager.startBatch()
        undoManager.append(ACTION0)
        assertEquals(ACTION0, undoManager.undo())
        assertFalse(undoManager.isInBatchMode)
    }

    @Test
    fun `should end batch on redo`() {
        undoManager.append(ACTION0)
        undoManager.undo()

        undoManager.startBatch()
        assertEquals(ACTION0, undoManager.redo())
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
    fun `should discard old actions if too many`() {
        undoManager.maxActions = 2
        undoManager.append(ACTION0)
        undoManager.append(ACTION1)
        undoManager.append(ACTION2)

        undoManager.undo()
        undoManager.undo()
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `should clear all actions`() {
        undoManager.append(ACTION0)
        undoManager.startBatch()
        undoManager.append(ACTION1)

        undoManager.clear()
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)
        assertFalse(undoManager.isInBatchMode)
    }

    companion object {
        private val ACTION0 = TextUndoAction.create(UndoActionLocation.Title, 0, 1, "a", "b")
        private val ACTION1 = TextUndoAction.create(UndoActionLocation.Title, 1, 2, "b", "c")
        private val ACTION01 = TextUndoAction.create(UndoActionLocation.Title, 0, 2, "ab", "bc")
        private val ACTION2 = TextUndoAction.create(UndoActionLocation.Content, 0, 1, "b", "c")
    }
}
