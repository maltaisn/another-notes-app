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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.*
import java.util.*
import kotlin.math.absoluteValue


class NoteAdapter(val context: Context, val callback: Callback) :
        ListAdapter<NoteListItem, RecyclerView.ViewHolder>(NoteListDiffCallback()) {

    /**
     * A pool of view holders for showing items of list notes.
     * When list note items are bound, view holders are obtained from this pool and bound.
     * When list note items are recycled, view holders are added back to the pool.
     */
    private val listNoteItemsPool = LinkedList<ListNoteItemViewHolder>()

    var listLayoutMode = NoteListLayoutMode.LIST
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled() = false
        override fun isItemViewSwipeEnabled() = callback.isNoteSwipeEnabled

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                makeMovementFlags(0, if (viewHolder is NoteViewHolder) {
                    ItemTouchHelper.START or ItemTouchHelper.END
                } else {
                    0
                })

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView,
                                 viewHolder: RecyclerView.ViewHolder,
                                 dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val view = viewHolder.itemView
            view.alpha = ((view.width - dX.absoluteValue * 0.7f) / view.width).coerceIn(0f, 1f)
            view.translationX = dX
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            val view = viewHolder.itemView
            view.alpha = 1f
            view.translationX = 0f
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            callback.onNoteSwiped(viewHolder.adapterPosition)
        }
    })

    val highlightBackgroundColor = ContextCompat.getColor(context, R.color.color_highlight)
    val highlightForegroundColor = ContextCompat.getColor(context, R.color.color_on_highlight)


    init {
        setHasStableIds(true)
    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MESSAGE -> MessageViewHolder(ItemMessageBinding.inflate(inflater, parent, false))
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_TEXT_NOTE -> TextNoteViewHolder(ItemNoteTextBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LIST_NOTE -> ListNoteViewHolder(ItemNoteListBinding.inflate(inflater, parent, false))
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is MessageViewHolder -> holder.bind(item as MessageItem, this)
            is HeaderViewHolder -> holder.bind(item as HeaderItem)
            is NoteViewHolder -> {
                if (holder is ListNoteViewHolder) {
                    unbindListNoteViewHolder(holder)
                }
                holder.bind(this, item as NoteItem)
            }
        }
    }


    override fun getItemViewType(position: Int) = getItem(position).type

    override fun getItemId(position: Int) = getItem(position).id

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ListNoteViewHolder) {
            unbindListNoteViewHolder(holder)
        }
    }

    private fun unbindListNoteViewHolder(holder: ListNoteViewHolder) {
        val vhs = holder.unbind()
        listNoteItemsPool += vhs
    }

    @SuppressLint("InflateParams")
    fun obtainListNoteItemViewHolder(): ListNoteItemViewHolder =
            if (listNoteItemsPool.isNotEmpty()) {
                listNoteItemsPool.pop()
            } else {
                ListNoteItemViewHolder(ItemNoteListItemBinding.inflate(
                        LayoutInflater.from(context), null, false))
            }

    interface Callback {
        fun onNoteItemClicked(item: NoteItem, pos: Int)
        fun onNoteItemLongClicked(item: NoteItem, pos: Int)
        fun onMessageItemDismissed(item: MessageItem, pos: Int)

        val isNoteSwipeEnabled: Boolean
        fun onNoteSwiped(pos: Int)

    }

    companion object {
        const val VIEW_TYPE_MESSAGE = 0
        const val VIEW_TYPE_HEADER = 1
        const val VIEW_TYPE_TEXT_NOTE = 10
        const val VIEW_TYPE_LIST_NOTE = 11
    }
}
