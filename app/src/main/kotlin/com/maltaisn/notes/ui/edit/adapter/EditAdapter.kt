/*
 * Copyright 2020 Nicolas Maltais
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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.EditViewModel
import java.util.*


class EditAdapter(val context: Context, val callback: Callback) :
        ListAdapter<EditListItem, RecyclerView.ViewHolder>(EditDiffCallback()) {

    private var recyclerView: RecyclerView? = null

    private var listItems: MutableList<EditListItem> = mutableListOf()

    private var pendingFocusChange: EditViewModel.FocusChange? = null


    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        // Note: to achieve the shadow effect on the dragged item, the EditTitleViewHolder
        // item view was set to the desired elevation, with outlineProvider set to `none`.
        // This way, when dragging an item, ItemTouchHelper will find this elevation value
        // in `onChildDraw` and will set the dragged item elevation just above that value.
        // See answer at [https://stackoverflow.com/a/60896887/5288316].

        override fun getMovementFlags(recyclerView: RecyclerView,
                                      viewHolder: RecyclerView.ViewHolder) =
                makeFlag(ItemTouchHelper.ACTION_STATE_DRAG,
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN)

        override fun isLongPressDragEnabled() = false

        override fun canDropOver(recyclerView: RecyclerView,
                                 current: RecyclerView.ViewHolder,
                                 target: RecyclerView.ViewHolder) = target is EditItemViewHolder

        override fun onMove(recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.adapterPosition
            val to = target.adapterPosition

            // submitList is not used here, since it results in a very unresponsive design.
            // Adapter and dataset are updated manually.
            notifyItemMoved(from, to)
            Collections.swap(listItems, from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    })

    override fun submitList(list: MutableList<EditListItem>?) {
        super.submitList(list)

        // Save mutable view of list which is otherwise inacessible.
        listItems = list ?: mutableListOf()
    }

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
            VIEW_TYPE_TITLE -> EditTitleViewHolder(inflater.inflate(
                    R.layout.item_edit_title, parent, false))
            VIEW_TYPE_CONTENT -> EditContentViewHolder(inflater.inflate(
                    R.layout.item_edit_content, parent, false))
            VIEW_TYPE_ITEM_ADD -> EditItemAddViewHolder(inflater.inflate(
                    R.layout.item_edit_item_add, parent, false), callback)
            VIEW_TYPE_ITEM -> {
                val viewHolder = EditItemViewHolder(inflater.inflate(
                        R.layout.item_edit_item, parent, false), callback)
                viewHolder.dragImv.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_DOWN && listItems.size > 3) {
                        // Drag handle was touched. Hide keyboard and start dragging.
                        viewHolder.clearFocus()
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .hideSoftInputFromWindow(view.windowToken, 0)
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
            is EditTitleViewHolder -> holder.bind(item as EditTitleItem)
            is EditContentViewHolder -> holder.bind(item as EditContentItem)
            is EditItemViewHolder -> {
                holder.bind(item as EditItemItem)
                if (position == pendingFocusChange?.itemPos) {
                    holder.setFocus(pendingFocusChange!!.pos)
                }
            }
        }
    }

    override fun getItemViewType(position: Int) = getItem(position).type


    fun setItemFocus(focus: EditViewModel.FocusChange) {
        val rcv = recyclerView ?: return

        // If item to focus on doesn't exist yet, save it for later.
        if (!focus.itemExists) {
            pendingFocusChange = focus
            return
        }

        val viewHolder = rcv.findViewHolderForAdapterPosition(focus.itemPos)
        if (viewHolder is EditItemViewHolder) {
            viewHolder.setFocus(focus.pos)
        } else {
            // No item view holder for that position.
            // Not supposed to happen, but if it does, just save it for later.
            pendingFocusChange = focus
        }
    }

    interface Callback {
        fun onNoteItemChanged(item: EditItemItem, pos: Int, isPaste: Boolean)
        fun onNoteItemBackspacePressed(item: EditItemItem, pos: Int)
        fun onNoteItemDeleteClicked(pos: Int)
        fun onNoteItemAddClicked()
    }

    companion object {
        const val VIEW_TYPE_TITLE = 0
        const val VIEW_TYPE_CONTENT = 1
        const val VIEW_TYPE_ITEM = 2
        const val VIEW_TYPE_ITEM_ADD = 3
    }
}
