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

import com.maltaisn.notes.ui.edit.EditFocusChange
import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import java.util.Collections

data class ItemChangeEventItem(
    val actualPos: Int,
    val text: String,
    val checked: Boolean,
) {
    companion object {
        fun fromItem(item: EditItemItem): ItemChangeEventItem {
            return ItemChangeEventItem(item.actualPos, item.text.text.toString(), item.checked)
        }
    }
}

/**
 * Insert new list items. Items must be sorted by actual pos.
 */
data class ItemAddEvent(val items: List<ItemChangeEventItem>) : ItemEditEvent {

    override fun undo(payload: EventPayload): EditFocusChange? {
        removeItems(payload, items)
        return null
    }

    override fun redo(payload: EventPayload): EditFocusChange? {
        addItems(payload, items)
        return null
    }
}

/**
 * Remove existing list items. Items must be sorted by actual pos.
 */
data class ItemRemoveEvent(
    val items: List<ItemChangeEventItem>
) : ItemEditEvent {

    override fun undo(payload: EventPayload): EditFocusChange? {
        addItems(payload, items)
        return null
    }

    override fun redo(payload: EventPayload): EditFocusChange? {
        removeItems(payload, items)
        return null
    }
}

/**
 * Change the check status of the items at [actualPos] to [checked].
 */
data class ItemCheckEvent(
    val actualPos: List<Int>,
    val checked: Boolean,
    val checkedByUser: Boolean,
) : ItemEditEvent {

    override fun undo(payload: EventPayload): EditFocusChange? {
        checkItems(payload, actualPos, !checked, checkedByUser)
        return null
    }

    override fun redo(payload: EventPayload): EditFocusChange? {
        checkItems(payload, actualPos, checked, checkedByUser)
        return null
    }
}

/**
 * Re-order list items according to the [newOrder] index list (actual positions).
 */
data class ItemReorderEvent(
    val newOrder: List<Int>,
) : ItemEditEvent {

    private fun changeItemsOrder(payload: EventPayload, order: List<Int>) {
        changeListItemsSortedByActualPos(payload) { items ->
            val oldItems = items.toList()
            for (i in items.indices) {
                items[i] = oldItems[order[i]].apply { actualPos = i }
            }
        }
    }

    override fun undo(payload: EventPayload): EditFocusChange? {
        val oldOrder = newOrder.withIndex().sortedBy { it.value }.map { it.index }
        changeItemsOrder(payload, oldOrder)
        return null
    }

    override fun redo(payload: EventPayload): EditFocusChange? {
        changeItemsOrder(payload, newOrder)
        return null
    }
}

/**
 * Swap list items [from] an actual position [to] another.
 * Items are either both checked or both unchecked.
 */
data class ItemSwapEvent(
    val from: Int,
    val to: Int
) : ItemEditEvent {

    override fun undo(payload: EventPayload): EditFocusChange? {
        return redo(payload)
    }

    override fun redo(payload: EventPayload): EditFocusChange? {
        val items = payload.listItems

        val fromIndex = items.indexOfFirst { it is EditItemItem && it.actualPos == from }
        val toIndex = items.indexOfFirst { it is EditItemItem && it.actualPos == to }

        (items[fromIndex] as EditItemItem).actualPos = to
        (items[toIndex] as EditItemItem).actualPos = from

        Collections.swap(items, fromIndex, toIndex)
        return null
    }
}

private fun checkItems(payload: EventPayload, actualPos: List<Int>, checked: Boolean, checkedByUser: Boolean) {
    changeListItemsSortedByActualPos(payload) { items ->
        for (pos in actualPos) {
            items[pos].checked = checked
            if (!checkedByUser) {
                items[pos].requestUpdate()
            }
        }
    }
}

private fun removeItems(payload: EventPayload, items: List<ItemChangeEventItem>) {
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

private fun addItems(payload: EventPayload, items: List<ItemChangeEventItem>) {
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

/**
 * Extract list items, sort them by actual position.
 * When [action] is called, the actual position is equal to the index.
 * The action must leave the items sorted by actual position.
 * The list is rebuilt according to the checked state of the items changed by the action.
 */
private fun changeListItemsSortedByActualPos(payload: EventPayload, action: (MutableList<EditItemItem>) -> Unit) {
    val items = payload.listItems
    val afterTitleIndex = items.indexOfFirst { it is EditTitleItem } + 1
    val itemsByActualPos: MutableList<EditItemItem>
    if (payload.moveCheckedToBottom) {
        itemsByActualPos = items.asSequence().filterIsInstance<EditItemItem>().toMutableList()
        itemsByActualPos.sortBy { it.actualPos }
    } else {
        var afterLastIndex = items.indexOfLast { it is EditItemItem } + 1
        if (afterLastIndex == 0) {
            afterLastIndex = afterTitleIndex
        }
        @Suppress("UNCHECKED_CAST")
        itemsByActualPos = items.subList(afterTitleIndex, afterLastIndex) as MutableList<EditItemItem>
    }

    // Do whatever on the list items
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
    var index = afterTitleIndex
    items.addAll(index, itemsByActualPos)
    index += itemsByActualPos.size
    items.add(index, EditItemAddItem)
    index++
    if (checkedItems.isNotEmpty()) {
        items.add(index, EditCheckedHeaderItem(checkedItems.size))
        index++
        items.addAll(index, checkedItems)
    }
}
