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

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.EditViewModel


class EditAdapter(val context: Context) :
        ListAdapter<EditListItem, RecyclerView.ViewHolder>(EditDiffCallback()) {

    private var recyclerView: RecyclerView? = null

    private var pendingFocusChange: EditViewModel.FocusChange? = null


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TITLE -> EditTitleViewHolder(inflater.inflate(
                    R.layout.item_edit_title, parent, false))
            VIEW_TYPE_CONTENT -> EditContentViewHolder(inflater.inflate(
                    R.layout.item_edit_content, parent, false))
            VIEW_TYPE_ITEM -> EditItemViewHolder(inflater.inflate(
                    R.layout.item_edit_item, parent, false))
            VIEW_TYPE_ITEM_ADD -> EditItemAddViewHolder(inflater.inflate(
                    R.layout.item_edit_item_add, parent, false))
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is EditTitleViewHolder -> holder.bind(item as EditTitleItem)
            is EditContentViewHolder -> holder.bind(item as EditContentItem)
            is EditItemAddViewHolder -> holder.bind(item as EditItemAddItem)
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

    companion object {
        const val VIEW_TYPE_TITLE = 0
        const val VIEW_TYPE_CONTENT = 1
        const val VIEW_TYPE_ITEM = 2
        const val VIEW_TYPE_ITEM_ADD = 3
    }
}
