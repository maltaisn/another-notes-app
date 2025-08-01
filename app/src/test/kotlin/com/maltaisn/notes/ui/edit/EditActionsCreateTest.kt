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

import com.maltaisn.notes.ui.edit.actions.EditAction
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability.AVAILABLE
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability.HIDDEN
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability.UNAVAILABLE
import com.maltaisn.notes.ui.edit.actions.createDialogItemsForEditActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability as Availability

class EditActionsCreateTest {

    @Test
    fun `should update items for edit actions`() {
        val toolbarItems = mutableMapOf<Int, Pair<Boolean, Boolean>>()

        val actions = listOf(
            testAction(0, true, AVAILABLE),
            testAction(1, true, UNAVAILABLE),
            testAction(2, true, HIDDEN),
            testAction(3, true, AVAILABLE),
            testAction(4, true, AVAILABLE),
            testAction(5, false, HIDDEN),
            testAction(6, false, AVAILABLE),
        )
        val maxInToolbar = 3

        val showOverflow = updateToolbarItemsForEditActions(maxInToolbar, actions) { pos, visible, enabled ->
            toolbarItems[pos] = visible to enabled
        }
        assertTrue(showOverflow)
        assertEquals(mapOf(
            0 to (true to true),
            1 to (true to false),
            2 to (false to false),
            3 to (true to true),
            4 to (false to false),
            5 to (false to false),
            6 to (false to false),
        ), toolbarItems)

        val overflowItems = mutableListOf<EditAction>()
        createDialogItemsForEditActions(maxInToolbar, actions) {
            overflowItems += it
        }
        assertEquals(listOf(4, 6), overflowItems.map { it.title })
    }

    @Test
    fun `should update items for edit actions (no overflow)`() {
        val items = mutableMapOf<Int, Pair<Boolean, Boolean>>()

        val actions = listOf(
            testAction(0, true, UNAVAILABLE),
            testAction(1, false, HIDDEN),
            testAction(2, false, HIDDEN),
        )
        val maxInToolbar = 1

        val showOverflow = updateToolbarItemsForEditActions(maxInToolbar, actions) { pos, visible, enabled ->
            items[pos] = visible to enabled
        }
        assertFalse(showOverflow)
        assertEquals(mapOf(
            0 to (true to false),
            1 to (false to false),
            2 to (false to false),
        ), items)

        val overflowItems = mutableListOf<EditAction>()
        createDialogItemsForEditActions(maxInToolbar, actions) {
            overflowItems += it
        }
        assertEquals(emptyList(), overflowItems)
    }

    private fun testAction(id: Int, showInToolbar: Boolean, available: Availability): EditAction {
        return EditAction(available, id, 0, showInToolbar, {})
    }
}
