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

package com.maltaisn.notes.ui.note.adapter

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.card.MaterialCardView
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.*
import com.maltaisn.notes.ui.note.HighlightHelper
import kotlin.math.min


abstract class NoteViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

    protected abstract val cardView: MaterialCardView
    protected abstract val titleTxv: TextView

    open fun bind(adapter: NoteAdapter, item: NoteItem) {
        titleTxv.text = HighlightHelper.getHighlightedText(item.note.title, item.titleHighlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
        titleTxv.isVisible = item.note.title.isNotBlank()

        cardView.isChecked = item.checked
        cardView.setOnClickListener {
            adapter.callback.onNoteItemClicked(item, adapterPosition)
        }
        cardView.setOnLongClickListener {
            adapter.callback.onNoteItemLongClicked(item, adapterPosition)
            true
        }
    }
}

class TextNoteViewHolder(private val binding: ItemNoteTextBinding) :
        NoteViewHolder(binding.root) {

    override val cardView = binding.cardView
    override val titleTxv = binding.titleTxv

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.TEXT)

        val contentTxv = binding.contentTxv
        contentTxv.isVisible = item.note.content.isNotBlank()
        contentTxv.text = HighlightHelper.getHighlightedText(item.note.content, item.contentHighlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
        contentTxv.maxLines = adapter.listLayoutMode.maxTextLines
    }
}

class ListNoteViewHolder(private val binding: ItemNoteListBinding) :
        NoteViewHolder(binding.root) {

    override val cardView = binding.cardView
    override val titleTxv = binding.titleTxv

    private val itemViewHolders = mutableListOf<ListNoteItemViewHolder>()

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.LIST)
        require(itemViewHolders.isEmpty())

        val noteItems = item.note.listItems

        val itemsLayout = binding.itemsLayout
        itemsLayout.isVisible = noteItems.isNotEmpty()

        // Add the first fewitems in list note using view holders in pool.
        val maxItems = adapter.listLayoutMode.maxListItems
        val itemHighlights = HighlightHelper.splitListNoteHighlightsByItem(
                noteItems, item.contentHighlights)
        for (i in 0 until min(maxItems, noteItems.size)) {
            val noteItem = noteItems[i]
            val viewHolder = adapter.obtainListNoteItemViewHolder()
            itemViewHolders += viewHolder
            viewHolder.bind(adapter, noteItem, itemHighlights[i])
            itemsLayout.addView(viewHolder.binding.root, i)
        }

        // Show a label indicating the number of items not shown.
        val infoTxv = binding.infoTxv
        val overflowCount = noteItems.size - maxItems
        infoTxv.isVisible = overflowCount > 0
        if (overflowCount > 0) {
            infoTxv.text = adapter.context.resources.getQuantityString(
                    R.plurals.note_list_item_info, overflowCount, overflowCount)
        }
    }

    /**
     * Unbind [ListNoteViewHolder] used in this item and return them.
     */
    fun unbind(): List<ListNoteItemViewHolder> {
        if (itemViewHolders.isEmpty()) {
            return emptyList()
        }

        // Free view holders used by the item.
        val viewHolders = itemViewHolders.toList()
        binding.itemsLayout.removeAllViews()
        itemViewHolders.clear()

        return viewHolders
    }
}

class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

    fun bind(item: MessageItem, adapter: NoteAdapter) {
        binding.messageTxv.text = adapter.context.getString(item.message, *item.args.toTypedArray())
        binding.closeImv.setOnClickListener {
            adapter.callback.onMessageItemDismissed(item, adapterPosition)
            adapter.notifyItemRemoved(adapterPosition)
        }

        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

    fun bind(item: HeaderItem) {
        binding.titleTxv.setText(item.title)
        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

/**
 * A view holder for displayed an item in a list note view holder.
 * This is effectively a view holder in a view holder...
 */
class ListNoteItemViewHolder(val binding: ItemNoteListItemBinding) {

    fun bind(adapter: NoteAdapter, item: ListNoteItem, highlights: List<IntRange>) {
        binding.contentTxv.text = HighlightHelper.getHighlightedText(item.content, highlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)

        binding.checkboxImv.setImageResource(if (item.checked) {
            R.drawable.ic_checkbox_on
        } else {
            R.drawable.ic_checkbox_off
        })
    }
}
