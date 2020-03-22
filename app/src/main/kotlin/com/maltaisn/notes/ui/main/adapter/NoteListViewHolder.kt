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

package com.maltaisn.notes.ui.main.adapter

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import kotlin.math.min


abstract class NoteViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

    private val titleTxv: TextView = itemView.findViewById(R.id.txv_title)

    protected open fun bind(item: NoteItem) {
        val title = item.note.title
        titleTxv.text = title
        titleTxv.isVisible = title.isNotBlank()
    }

}

class TextNoteViewHolder(itemView: View) : NoteViewHolder(itemView) {

    private val contentTxv: TextView = itemView.findViewById(R.id.txv_content)

    public override fun bind(item: NoteItem) {
        super.bind(item)
        require(item.note.type == NoteType.TEXT)

        contentTxv.text = item.note.content
    }
}

class ListNoteViewHolder(itemView: View) : NoteViewHolder(itemView) {

    private val layout: LinearLayout = itemView.findViewById(R.id.layout_list_items)
    private val infoTxv: TextView = itemView.findViewById(R.id.txv_info)

    private val itemViewHolders = mutableListOf<ListNoteItemViewHolder>()

    fun bind(item: NoteItem, adapter: NoteAdapter) {
        super.bind(item)
        require(item.note.type == NoteType.LIST)

        // Add first items in list using view holders in pool.
        // Only the first few items are shown.
        val noteItems = item.note.getListItems(adapter.json)
        for (i in 0 until min(MAX_LIST_ITEMS_SHOWN, noteItems.size)) {
            val noteItem = noteItems[i]
            val viewHolder = adapter.obtainListNoteItemViewHolder()
            itemViewHolders += viewHolder
            viewHolder.bind(noteItem)
            layout.addView(viewHolder.itemView, i + 1)
        }

        // Show a label indicating the number of items not shown.
        val overflowCount = noteItems.size - MAX_LIST_ITEMS_SHOWN
        infoTxv.isVisible = overflowCount > 0
        if (overflowCount > 0) {
            infoTxv.text = adapter.context.resources.getQuantityString(
                    R.plurals.note_list_item_info, overflowCount, overflowCount)
        }
    }

    fun unbind(): List<ListNoteItemViewHolder> {
        // Free view holders used by the item.
        val viewHolders = itemViewHolders.toList()
        layout.removeViews(1, layout.childCount - 2)
        itemViewHolders.clear()
        return viewHolders
    }

    companion object {
        private const val MAX_LIST_ITEMS_SHOWN = 5
    }

}

class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val messageTxv: TextView = itemView.findViewById(R.id.txv_message)
    private val closeBtn: ImageView = itemView.findViewById(R.id.imv_message_close)

    fun bind(item: MessageItem) {
        messageTxv.text = messageTxv.context.getString(item.message, *item.args)
        closeBtn.setOnClickListener {
            item.onDismiss()
        }
    }

}

class ListNoteItemViewHolder(val itemView: View) {

    private val checkImv: ImageView = itemView.findViewById(R.id.imv_item_checkbox)
    private val titleTxv: TextView = itemView.findViewById(R.id.txv_item_title)

    fun bind(item: ListNoteItem) {
        titleTxv.text = item.content
        checkImv.setImageResource(if (item.checked) {
            R.drawable.ic_checkbox_on
        } else {
            R.drawable.ic_checkbox_off
        })
    }

}
