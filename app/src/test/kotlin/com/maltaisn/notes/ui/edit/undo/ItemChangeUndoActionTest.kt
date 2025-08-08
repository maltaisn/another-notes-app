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
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.e
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ItemChangeUndoActionTest {

    private val editableTextProvider = TestEditableTextProvider()

    @Test
    fun `should redo and undo correctly (add)`() {
        val action = ItemAddUndoAction(listOf(
            ItemChangeUndoActionItem(1, "new item 1", true),
            ItemChangeUndoActionItem(2, "new item 2", false),
        ))

        val listItemsBefore = ITEMS0
        val listItemsAfter = ITEMS1
        val listItems = listItemsBefore.copy()
        val payload = UndoPayload(editableTextProvider, listItems)

        action.redo(payload)
        assertEquals(listItemsAfter, listItems)

        action.undo(payload)
        assertEquals(listItemsBefore, listItems)
    }

    @Test
    fun `should redo and undo correctly (remove)`() {
        val action = ItemRemoveUndoAction(listOf(
            ItemChangeUndoActionItem(1, "new item 1", true),
            ItemChangeUndoActionItem(2, "new item 2", false),
        ))

        val listItemsBefore = ITEMS1
        val listItemsAfter = ITEMS0
        val listItems = listItemsBefore.copy()
        val payload = UndoPayload(editableTextProvider, listItems)

        action.redo(payload)
        assertEquals(listItemsAfter, listItems)

        action.undo(payload)
        assertEquals(listItemsBefore, listItems)
    }

    @Test
    fun `should redo and undo correctly (check)`() {
        val action = ItemCheckUndoAction(
            actualPos = listOf(0, 2),
            checked = true,
            checkedByUser = true,
        )

        val listItemsBefore = ITEMS0
        val listItemsAfter = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = true, editable = true, actualPos = 0),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("item 3".e, checked = true, editable = true, actualPos = 2),
        )

        val listItems = listItemsBefore.copy()
        val payload = UndoPayload(editableTextProvider, listItems)

        action.redo(payload)
        assertEquals(listItemsAfter, listItems)

        action.undo(payload)
        assertEquals(listItemsBefore, listItems)
    }

    @Test
    fun `should redo and undo correctly (reorder)`() {
        val action = ItemReorderUndoAction(listOf(2, 1, 0, 4, 3))

        val listItemsBefore = ITEMS1
        val listItemsAfter = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("new item 2".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("new item 1".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 2),
            EditItemItem("item 3".e, checked = false, editable = true, actualPos = 3),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 4),
        )

        val listItems = listItemsBefore.copy()
        val payload = UndoPayload(editableTextProvider, listItems)

        action.redo(payload)
        assertEquals(listItemsAfter, listItems)

        action.undo(payload)
        assertEquals(listItemsBefore, listItems)
    }

    @Test
    fun `should redo and undo correctly (swap)`() {
        val action = ItemSwapUndoAction(2, 0)

        val listItemsBefore = ITEMS0
        val listItemsAfter = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("item 3".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 2),
        )

        val listItems = listItemsBefore.copy()
        val payload = UndoPayload(editableTextProvider, listItems)

        action.redo(payload)
        assertEquals(listItemsAfter, listItems)

        action.undo(payload)
        assertEquals(listItemsBefore, listItems)
    }

    private fun checkIfListItemsAreCorrect(listItems: List<EditListItem>, moveCheckedToBottom: Boolean = false) {
        var checkedHeader: EditCheckedHeaderItem? = null
        var foundAddItem = false
        var foundCheckedItems = false
        var checkedCount = 0
        var lastActualPos = -1
        for ((i, item) in listItems.withIndex()) {
            when (item) {
                is EditTitleItem -> {
                    assert(i == 0)
                }
                is EditItemItem -> {
                    if (moveCheckedToBottom) {
                        assert(item.actualPos > lastActualPos)
                    } else {
                        assert(item.actualPos == lastActualPos + 1)
                        assert(!foundAddItem)
                    }
                    lastActualPos = item.actualPos
                    if (item.checked) {
                        if (moveCheckedToBottom) {
                            assert(foundAddItem)
                            assertNotNull(checkedHeader)
                        }
                        checkedCount++
                        foundCheckedItems = true
                    }
                }
                is EditItemAddItem -> {
                    assert(!moveCheckedToBottom || !foundCheckedItems)
                    foundAddItem = true
                }
                is EditCheckedHeaderItem -> {
                    assert(moveCheckedToBottom)
                    assert(!foundCheckedItems)
                    checkedHeader = item
                    lastActualPos = -1
                }
                else -> error("")
            }
        }
        if (checkedCount > 0 && moveCheckedToBottom) {
            assert(checkedHeader!!.count == checkedCount)
        }
    }

    private fun randomTest(moveCheckedToBottom: Boolean) {
        val initialItems = mutableListOf(
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("item 2".e, checked = false, editable = true, actualPos = 1),
            EditItemAddItem,
        )
        val listItems = initialItems.copy()
        val payload = UndoPayload(editableTextProvider, listItems, moveCheckedToBottom)

        val rng = Random(0)
        val actions = mutableListOf<ItemUndoAction>()
        repeat(1000) {
            var items = listItems.filterIsInstance<EditItemItem>()
            val action = when ((0..4).random(rng)) {
                0 -> {
                    var checked = rng.nextBoolean()
                    if (items.all { it.checked == checked }) {
                        checked = !checked
                    }
                    items = items.filter { it.checked != checked }
                    items = items.shuffled(rng).take((0..items.size).random(rng))
                    val actualPos = items.map { it.actualPos }
                    ItemCheckUndoAction(actualPos, checked, true)
                }
                1 -> {
                    val count = (0..5).random(rng)
                    val indices = (0..(items.lastIndex + count)).shuffled(rng).take(count).sorted()
                    ItemAddUndoAction(indices.map {
                        ItemChangeUndoActionItem(it, randomString(0..10, rng), rng.nextBoolean())
                    })
                }
                2 -> {
                    val count = (0..minOf(5, items.size)).random(rng)
                    val items = items.shuffled(rng).take(count).sortedBy { it.actualPos }
                    ItemRemoveUndoAction(items.map(ItemChangeUndoActionItem::fromItem))
                }
                3 -> {
                    val actualPos = items.mapTo(mutableListOf()) { it.actualPos }
                    actualPos.shuffle(rng)
                    ItemReorderUndoAction(actualPos)
                }
                4 -> {
                    if (items.isEmpty()) {
                        return@repeat
                    }
                    var checked = rng.nextBoolean()
                    if (items.none { it.checked == checked }) {
                        checked = !checked
                    }
                    val items = items.filter { it.checked == checked }.map { it.actualPos }
                    val from = items.random(rng)
                    val to = items.random(rng)
                    ItemSwapUndoAction(from, to)
                }
                else -> error("")
            }

            action.redo(payload)
            actions += action

            checkIfListItemsAreCorrect(listItems, moveCheckedToBottom)
        }

        for (action in actions.asReversed()) {
            action.undo(payload)
        }
        assertEquals(initialItems, listItems)
    }

    @Test
    fun `should redo and undo correctly (random)`() {
        randomTest(moveCheckedToBottom = false)
    }

    @Test
    fun `should redo and undo correctly (random, move checked to bottom)`() {
        randomTest(moveCheckedToBottom = true)
    }

    companion object {
        private val ITEMS0 = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("item 3".e, checked = false, editable = true, actualPos = 2),
        )
        private val ITEMS1 = listOf<EditListItem>(
            EditTitleItem("title".e, true),
            EditItemItem("item 1".e, checked = false, editable = true, actualPos = 0),
            EditItemItem("new item 1".e, checked = true, editable = true, actualPos = 1),
            EditItemItem("new item 2".e, checked = false, editable = true, actualPos = 2),
            EditItemItem("item 2".e, checked = true, editable = true, actualPos = 3),
            EditItemItem("item 3".e, checked = false, editable = true, actualPos = 4),
        )
    }
}
