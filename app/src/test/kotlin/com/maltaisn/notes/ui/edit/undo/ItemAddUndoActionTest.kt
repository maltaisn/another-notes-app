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

import com.maltaisn.notes.ui.edit.EditFocusChange
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.e
import org.junit.Test
import kotlin.test.assertEquals

class ItemAddUndoActionTest {

    @Test
    fun `should redo and undo correctly`() {
        val action = ItemAddUndoAction(
            itemPos = 2,
            itemCount = 2,
            text = "new item 1\nnew item 2",
            checked = true,
            actualPos = 1,
            focusBefore = EditFocusChange(1, 3, true),
        )

        val listItemsBefore = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("item 3".e, checked = false, editable = true, actualPos = 2),
        )
        val listItemsAfter = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("new item 1".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("new item 2".e, checked = true, editable = true, actualPos = 2),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 3),
            EditItemItem("item 3".e, checked = false, editable = true, actualPos = 4),
        )

        val listItems = listItemsBefore.mapTo(mutableListOf<EditListItem>()) {
            when (it) {
                is EditTitleItem -> it.copy()
                is EditItemItem -> it.copy()
                else -> error("")
            }
        }

        action.redo(listItems)
        assertEquals(listItemsAfter, listItems)

        action.undo(listItems)
        assertEquals(listItemsBefore, listItems)
    }
}
