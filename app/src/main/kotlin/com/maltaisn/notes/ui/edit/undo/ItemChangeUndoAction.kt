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
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import java.util.Collections

data class ItemChangeUndoActionItem(
    val actualPos: Int,
    val text: String,
    val checked: Boolean,
) {
    companion object {
        fun fromItem(item: EditItemItem): ItemChangeUndoActionItem {
            return ItemChangeUndoActionItem(item.actualPos, item.text.text.toString(), item.checked)
        }
    }
}

/**
 * Insert new list items. Items must be sorted by actual pos.
 */
data class ItemAddUndoAction(val items: List<ItemChangeUndoActionItem>) : ItemUndoAction {

    override fun undo(payload: UndoPayload): EditFocusChange? {
        removeItems(payload, items)
        return null
    }

    override fun redo(payload: UndoPayload): EditFocusChange? {
        addItems(payload, items)
        return null
    }
}

/**
 * Remove existing list items. Items must be sorted by actual pos.
 */
data class ItemRemoveUndoAction(
    val items: List<ItemChangeUndoActionItem>
) : ItemUndoAction {

    override fun undo(payload: UndoPayload): EditFocusChange? {
        addItems(payload, items)
        return null
    }

    override fun redo(payload: UndoPayload): EditFocusChange? {
        removeItems(payload, items)
        return null
    }
}

/**
 * Change the check status of the items at [actualPos] to [checked].
 */
data class ItemCheckUndoAction(
    val actualPos: List<Int>,
    val checked: Boolean,
) : ItemUndoAction {

    override fun undo(payload: UndoPayload): EditFocusChange? {
        checkItems(payload, actualPos, !checked)
        return null
    }

    override fun redo(payload: UndoPayload): EditFocusChange? {
        checkItems(payload, actualPos, checked)
        return null
    }
}

/**
 * Re-order list items according to the [newOrder] index list (actual positions).
 */
data class ItemReorderUndoAction(
    val newOrder: List<Int>,
) : ItemUndoAction {

    private fun changeItemsOrder(payload: UndoPayload, order: List<Int>) {
        changeListItemsSortedByActualPos(payload) { items ->
            val oldItems = items.toList()
            for (i in items.indices) {
                items[i] = oldItems[order[i]].apply { actualPos = i }
            }
        }
    }

    override fun undo(payload: UndoPayload): EditFocusChange? {
        val oldOrder = newOrder.withIndex().sortedBy { it.value }.map { it.index }
        changeItemsOrder(payload, oldOrder)
        return null
    }

    override fun redo(payload: UndoPayload): EditFocusChange? {
        changeItemsOrder(payload, newOrder)
        return null
    }
}

/**
 * Swap list items [from] an actual position [to] another.
 * Items are either both checked or both unchecked.
 */
data class ItemSwapUndoAction(
    val from: Int,
    val to: Int
) : ItemUndoAction {

    override fun undo(payload: UndoPayload): EditFocusChange? {
        return redo(payload)
    }

    override fun redo(payload: UndoPayload): EditFocusChange? {
        val items = payload.listItems

        val fromIndex = items.indexOfFirst { it is EditItemItem && it.actualPos == from }
        val toIndex = items.indexOfFirst { it is EditItemItem && it.actualPos == to }

        (items[fromIndex] as EditItemItem).actualPos = to
        (items[toIndex] as EditItemItem).actualPos = from

        Collections.swap(items, fromIndex, toIndex)
        return null
    }
}

private fun checkItems(payload: UndoPayload, actualPos: List<Int>, checked: Boolean) {
    changeListItemsSortedByActualPos(payload) { items ->
        for (pos in actualPos) {
            // TODO this break animation because the item identity changes. Use an ID for all items instead!
            items[pos] = items[pos].copy(checked = checked)
        }
    }
}

private fun removeItems(payload: UndoPayload, items: List<ItemChangeUndoActionItem>) {
    changeListItemsSortedByActualPos(payload) { listItems ->
        var i = listItems.lastIndex
        var j = items.lastIndex
        while (i >= 0) {
            val item = listItems[i]
            item.actualPos -= j + 1
            if (j >= 0 && i == items[j].actualPos) {
                listItems.removeAt(i)
                j--
            }
            i--
        }
    }
}

private fun addItems(payload: UndoPayload, items: List<ItemChangeUndoActionItem>) {
    changeListItemsSortedByActualPos(payload) { listItems ->
        var i = 0
        var j = 0
        while (i <= listItems.size) {
            if (j < items.size && i == items[j].actualPos) {
                val item = items[j]
                listItems.add(i, EditItemItem(
                    text = payload.editableTextProvider.create(item.text),
                    checked = item.checked,
                    editable = true,
                    actualPos = item.actualPos
                ))
                j++
            } else if (i < listItems.size) {
                listItems[i].actualPos += j
            }
            i++
        }
    }
}

private fun changeListItemsSortedByActualPos(payload: UndoPayload, action: (MutableList<EditItemItem>) -> Unit) {
    // Extract list items, sort them by actual position
    // When extracted & sorted, the actual position is equal to the index.
    val items = payload.listItems
    val afterTitlePos = items.indexOfFirst { it is EditTitleItem } + 1
    val itemsByActualPos: MutableList<EditItemItem>
    if (payload.moveCheckedToBottom) {
        itemsByActualPos = items.asSequence().filterIsInstance<EditItemItem>().toMutableList()
        itemsByActualPos.sortBy { it.actualPos }
    } else {
        var afterLastPos = items.indexOfLast { it is EditItemItem } + 1
        if (afterLastPos == 0) {
            afterLastPos = afterTitlePos
        }
        @Suppress("UNCHECKED_CAST")
        itemsByActualPos = items.subList(afterTitlePos, afterLastPos) as MutableList<EditItemItem>
    }

    // Do whatever on the list items
    // We assume that the caller keep the list sorted
    action(itemsByActualPos)

    if (!payload.moveCheckedToBottom) {
        return
    }

    // Extract checked items
    val checkedItems = mutableListOf<EditItemItem>()
    for (i in itemsByActualPos.indices.reversed()) {
        val item = itemsByActualPos[i]
        if (item.checked) {
            checkedItems += item
            itemsByActualPos.removeAt(i)
        }
    }
    checkedItems.reverse()

    // Remove all list-related items
    items.removeAll { it is EditItemItem || it is EditCheckedHeaderItem || it is EditItemAddItem }

    // Add them back in correct order
    var pos = afterTitlePos
    items.addAll(pos, itemsByActualPos)
    pos += itemsByActualPos.size
    items.add(pos, EditItemAddItem)
    pos++
    if (checkedItems.isNotEmpty()) {
        items.add(pos, EditCheckedHeaderItem(checkedItems.size))
        pos++
        items.addAll(pos, checkedItems)
    }
}
