/*
 * Copyright 2021 Nicolas Maltais
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
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.ItemHeaderBinding
import com.maltaisn.notes.sync.databinding.ItemMessageBinding
import com.maltaisn.notes.sync.databinding.ItemNoteLabelBinding
import com.maltaisn.notes.sync.databinding.ItemNoteListBinding
import com.maltaisn.notes.sync.databinding.ItemNoteListItemBinding
import com.maltaisn.notes.sync.databinding.ItemNoteTextBinding
import com.maltaisn.notes.ui.note.SwipeAction

class NoteAdapter(
    val context: Context,
    val callback: Callback,
    val prefsManager: PrefsManager
) : ListAdapter<NoteListItem, RecyclerView.ViewHolder>(NoteListDiffCallback()) {

    /**
     * A pool of view holders for showing items of list notes.
     * When list note items are bound, view holders are obtained from this pool and bound.
     * When list note items are recycled, view holders are added back to the pool.
     * **Should only be accessed on main thread.**
     */
    private val listNoteItemViewHolderPool = ArrayDeque<ListNoteItemViewHolder>()

    /**
     * A pool of view holders for showing label chips
     * When note items are bound, view holders are obtained from this pool and bound.
     * When note items are recycled, view holders are added back to the pool.
     * **Should only be accessed on main thread.**
     */
    private val labelViewHolderPool = ArrayDeque<LabelChipViewHolder>()

    private val itemTouchHelper = ItemTouchHelper(SwipeTouchHelperCallback(callback))

    // Used by view holders with highlighted text.
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
            ViewType.MESSAGE.ordinal -> MessageViewHolder(ItemMessageBinding
                .inflate(inflater, parent, false))
            ViewType.HEADER.ordinal -> HeaderViewHolder(ItemHeaderBinding
                .inflate(inflater, parent, false))
            ViewType.TEXT_NOTE.ordinal -> TextNoteViewHolder(ItemNoteTextBinding
                .inflate(inflater, parent, false))
            ViewType.LIST_NOTE.ordinal -> ListNoteViewHolder(ItemNoteListBinding
                .inflate(inflater, parent, false))
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is MessageViewHolder -> holder.bind(item as MessageItem, this)
            is HeaderViewHolder -> holder.bind(item as HeaderItem)
            is TextNoteViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind(this)
                holder.bind(this, item as NoteItemText)
            }
            is ListNoteViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind(this)
                holder.bind(this, item as NoteItemList)
            }
        }
    }

    override fun getItemViewType(position: Int) = getItem(position).type.ordinal

    override fun getItemId(position: Int) = getItem(position).id

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        // Used to recycle secondary view holders
        if (holder is NoteViewHolder<*>) {
            holder.unbind(this)
        }
    }

    @SuppressLint("InflateParams")
    fun obtainListNoteItemViewHolder(): ListNoteItemViewHolder =
        if (listNoteItemViewHolderPool.isNotEmpty()) {
            listNoteItemViewHolderPool.removeLast()
        } else {
            ListNoteItemViewHolder(ItemNoteListItemBinding.inflate(
                LayoutInflater.from(context), null, false))
        }

    @SuppressLint("InflateParams")
    fun obtainLabelViewHolder(): LabelChipViewHolder =
        if (labelViewHolderPool.isNotEmpty()) {
            labelViewHolderPool.removeLast()
        } else {
            LabelChipViewHolder(ItemNoteLabelBinding.inflate(
                LayoutInflater.from(context), null, false))
        }

    fun freeListNoteItemViewHolder(viewHolder: ListNoteItemViewHolder) {
        listNoteItemViewHolderPool += viewHolder
    }

    fun freeLabelViewHolder(viewHolder: LabelChipViewHolder) {
        labelViewHolderPool += viewHolder
    }

    fun updateForListLayoutChange() {
        // Number of preview lines have changed, must rebind all items
        notifyItemRangeChanged(0, itemCount)
    }

    enum class ViewType {
        MESSAGE,
        HEADER,
        TEXT_NOTE,
        LIST_NOTE
    }

    enum class SwipeDirection {
        LEFT, RIGHT
    }

    interface Callback {
        /** Called when a note [item] at [pos] is clicked. */
        fun onNoteItemClicked(item: NoteItem, pos: Int)

        /** Called when a note [item] at [pos] is long-clicked. */
        fun onNoteItemLongClicked(item: NoteItem, pos: Int)

        /** Called when a message [item] at [pos] is dismissed by clicking on close button. */
        fun onMessageItemDismissed(item: MessageItem, pos: Int)

        /** Called when a note's action button is clicked. */
        fun onNoteActionButtonClicked(item: NoteItem, pos: Int)

        /** Returns the action for the given swipe direction. */
        fun getNoteSwipeAction(direction: SwipeDirection): SwipeAction

        /** Called when a [NoteItem] at [pos] is swiped. */
        fun onNoteSwiped(pos: Int, direction: NoteAdapter.SwipeDirection)

        /** Whether strikethrough should be added to checked items or not. */
        val strikethroughCheckedItems: Boolean
    }
}
