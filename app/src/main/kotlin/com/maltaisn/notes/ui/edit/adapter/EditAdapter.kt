/*
 * Copyright 2023 Nicolas Maltais
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

package com.maltaisn.notes.ui.edit.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.databinding.ItemEditContentBinding
import com.maltaisn.notes.databinding.ItemEditDateBinding
import com.maltaisn.notes.databinding.ItemEditHeaderBinding
import com.maltaisn.notes.databinding.ItemEditItemAddBinding
import com.maltaisn.notes.databinding.ItemEditItemBinding
import com.maltaisn.notes.databinding.ItemEditLabelsBinding
import com.maltaisn.notes.databinding.ItemEditTitleBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.ui.edit.EditViewModel

class EditAdapter(val context: Context, val callback: Callback) :
    ListAdapter<EditListItem, RecyclerView.ViewHolder>(EditDiffCallback()) {

    private var recyclerView: RecyclerView? = null

    private val itemTouchHelper = ItemTouchHelper(DragTouchHelperCallback(
        context, callback.moveCheckedToBottom) { from, to ->
        // submitList is not used here, since it results in a very unresponsive design.
        // Adapter and dataset are updated manually.
        notifyItemMoved(from, to)
        callback.onNoteItemSwapped(from, to)
    })

    /**
     * Pending focus change to be made when item will be bound
     * by RecyclerView, or `null` if none is pending.
     */
    private var pendingFocusChange: EditViewModel.FocusChange? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.DATE.ordinal -> EditDateViewHolder(ItemEditDateBinding
                .inflate(inflater, parent, false))
            ViewType.TITLE.ordinal -> EditTitleViewHolder(ItemEditTitleBinding
                .inflate(inflater, parent, false), callback)
            ViewType.CONTENT.ordinal -> EditContentViewHolder(ItemEditContentBinding
                .inflate(inflater, parent, false), callback)
            ViewType.ITEM_ADD.ordinal -> EditItemAddViewHolder(ItemEditItemAddBinding
                .inflate(inflater, parent, false), callback)
            ViewType.ITEM_CHECKED_HEADER.ordinal -> EditHeaderViewHolder(ItemEditHeaderBinding
                .inflate(inflater, parent, false))
            ViewType.ITEM_CHIPS.ordinal -> EditItemLabelsViewHolder(ItemEditLabelsBinding
                .inflate(inflater, parent, false), callback)
            ViewType.ITEM.ordinal -> {
                val viewHolder = EditItemViewHolder(ItemEditItemBinding
                    .inflate(inflater, parent, false), callback)
                viewHolder.dragImv.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_DOWN && callback.isNoteDragEnabled) {
                        // Drag handle was touched. Hide keyboard and start dragging.
                        viewHolder.clearFocus()
                        view.hideKeyboard()
                        itemTouchHelper.startDrag(viewHolder)
                    }
                    false
                }
                viewHolder
            }
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is EditDateViewHolder -> holder.bind(item as EditDateItem)
            is EditTitleViewHolder -> holder.bind(item as EditTitleItem)
            is EditContentViewHolder -> holder.bind(item as EditContentItem)
            is EditItemViewHolder -> holder.bind(item as EditItemItem)
            is EditHeaderViewHolder -> holder.bind(item as EditCheckedHeaderItem)
            is EditItemLabelsViewHolder -> holder.bind(item as EditChipsItem)
        }
        if (holder is EditFocusableViewHolder && position == pendingFocusChange?.itemPos) {
            // Apply pending focus change event.
            holder.setFocus(pendingFocusChange!!.pos)
            pendingFocusChange = null
        }
    }

    override fun getItemViewType(position: Int) = getItem(position).type.ordinal

    fun setItemFocus(focus: EditViewModel.FocusChange) {
        val rcv = recyclerView ?: return

        // If item to focus on doesn't exist yet, save it for later.
        if (!focus.itemExists) {
            pendingFocusChange = focus
            return
        }

        val viewHolder = rcv.findViewHolderForAdapterPosition(focus.itemPos)
        if (viewHolder is EditFocusableViewHolder) {
            viewHolder.setFocus(focus.pos)
        } else {
            // No item view holder for that position.
            // Not supposed to happen, but if it does, just save it for later.
            pendingFocusChange = focus
        }
    }

    enum class ViewType {
        DATE,
        TITLE,
        CONTENT,
        ITEM,
        ITEM_ADD,
        ITEM_CHECKED_HEADER,
        ITEM_CHIPS,
    }

    interface Callback {
        /**
         * Called when an [EditItemItem] at [pos] text is changed by user,
         * either from the keyboard or from a paste event.
         */
        fun onNoteItemChanged(pos: Int, isPaste: Boolean)

        /** Called when an [EditItemItem] at [pos] is checked or unchecked by user. */
        fun onNoteItemCheckChanged(pos: Int, checked: Boolean)

        /**
         * Called when backspace is pressed when EditText selection
         * is a position 0 in an [EditItemItem] at [pos].
         */
        fun onNoteItemBackspacePressed(pos: Int)

        /** Called when the delete button is clicked on an [EditItemItem].*/
        fun onNoteItemDeleteClicked(pos: Int)

        /** Called when [EditItemAddItem] is clicked.*/
        fun onNoteItemAddClicked(pos: Int)

        /** Called when a chip in [EditChipsItem] is clicked. */
        fun onNoteLabelClicked()
        fun onNoteReminderClicked()

        /** Called when any item is clicked on to start editing.*/
        fun onNoteClickedToEdit()

        /** Called when a link with an [url] is clicked in the note text.*/
        fun onLinkClickedInNote(linkText: String, linkUrl: String)

        /** Whether to enabled the dragging of [EditItemItem].*/
        val isNoteDragEnabled: Boolean

        /** Called after an [EditItemItem] was dragged [from] a position [to] another. */
        fun onNoteItemSwapped(from: Int, to: Int)

        /** Whether strikethrough should be added to checked items or not. */
        val strikethroughCheckedItems: Boolean

        /** Whether checked items are moved to the bottom or not. */
        val moveCheckedToBottom: Boolean
    }
}
