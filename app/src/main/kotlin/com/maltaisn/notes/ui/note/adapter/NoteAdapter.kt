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

package com.maltaisn.notes.ui.note.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.ItemHeaderBinding
import com.maltaisn.notes.databinding.ItemMessageBinding
import com.maltaisn.notes.databinding.ItemNoteLabelBinding
import com.maltaisn.notes.databinding.ItemNoteListBinding
import com.maltaisn.notes.databinding.ItemNoteListItemBinding
import com.maltaisn.notes.databinding.ItemNoteTextBinding
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.ui.note.StatusChangeAction
import kotlin.math.hypot

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

    private val itemTouchHelper = ItemTouchHelper(NoteTouchHelperCallback(callback,
        onSwipe = { viewHolder, pos, direction ->
            val item = getItem(pos) as NoteItem
            val swipeDir = if (direction == ItemTouchHelper.LEFT) SwipeDirection.LEFT else SwipeDirection.RIGHT
            callback.onNoteSwiped(item, pos, swipeDir)
        },
        onDrag = { viewHolder, from, to ->
            val item = getItem(from) as NoteItem
            // We could update the note in the database for each individual swap but this would result in a
            // very unresponsive design. Instead, adapter and dataset are updated manually until drag ends.
            notifyItemMoved(from, to)
            callback.onNoteSwapped(item, from, to)
        },
        canDropOver = { current, target ->
            if (target is NoteViewHolder<*>) {
                val currentItem = getItem(current.bindingAdapterPosition) as NoteItem
                val targetItem = getItem(target.bindingAdapterPosition) as NoteItem
                currentItem.note.pinned == targetItem.note.pinned
            } else {
                false
            }
        }))

    private var dragStarted = false
    private var dragStartPos: Int = RecyclerView.NO_POSITION

    // Used by view holders with highlighted text.
    val highlightBackgroundColor = ContextCompat.getColor(context, R.color.color_highlight)
    val highlightForegroundColor = ContextCompat.getColor(context, R.color.color_on_highlight)

    init {
        setHasStableIds(true)
    }

    private fun startDragging(recyclerView: RecyclerView) {
        if (!dragStarted) {
            dragStarted = true
            callback.onNoteDragStart()

            // Just as with long click, rebind manually the view holder to prevent it from being changed.
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(dragStartPos) as? NoteViewHolder<*> ?: return
            onBindViewHolder(viewHolder, dragStartPos)
        }
    }

    private fun stopDragging() {
        if (dragStarted) {
            callback.onNoteDragEnd()
            dragStarted = false
        }
        dragStartPos = RecyclerView.NO_POSITION
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView)
        recyclerView.setOnTouchListener(object : View.OnTouchListener {
            private var downX: Float? = null
            private var downY: Float? = null

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (downX == null || downY == null) {
                            // ACTION_DOWN events are not received, they must be consumed by the note card view.
                            // So take the first move point as the reference for drag distance.
                            downX = event.x
                            downY = event.y
                        } else if (!dragStarted && dragStartPos != RecyclerView.NO_POSITION) {
                            val item = getItem(dragStartPos) as NoteItem
                            val dragDistance = hypot(downX!! - event.x, downY!! - event.y)
                            val minDragDistance = v.resources.getDimensionPixelSize(R.dimen.note_drag_min_distance)
                            if (callback.canDrag(item) && dragDistance >= minDragDistance) {
                                startDragging(recyclerView)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        stopDragging()
                        downX = null
                        downY = null
                    }
                }
                return false
            }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.MESSAGE.ordinal -> MessageViewHolder(ItemMessageBinding
                .inflate(inflater, parent, false))
            ViewType.HEADER.ordinal -> HeaderViewHolder(ItemHeaderBinding
                .inflate(inflater, parent, false))
            ViewType.TEXT_NOTE.ordinal -> {
                val viewHolder = TextNoteViewHolder(ItemNoteTextBinding
                    .inflate(inflater, parent, false))
                attachLongClickListener(viewHolder)
                viewHolder
            }
            ViewType.LIST_NOTE.ordinal -> {
                val viewHolder = ListNoteViewHolder(ItemNoteListBinding
                    .inflate(inflater, parent, false))
                attachLongClickListener(viewHolder)
                viewHolder
            }
            else -> error("Unknown view type")
        }
    }

    private fun attachLongClickListener(viewHolder: NoteViewHolder<*>) {
        viewHolder.cardView.setOnLongClickListener {
            val pos = viewHolder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val item = getItem(pos) as NoteItem
                callback.onNoteItemLongClicked(item, pos)
                // Rebind manually the view holder to prevent it from being changed.
                // We start drag here, and if the view holder changed just after it wouldn't work.
                // Note: if drag is started then a note is unarchived by undo for example, the list will
                // be updated and the view holder might
                onBindViewHolder(viewHolder, pos)
                if (dragStartPos == RecyclerView.NO_POSITION && callback.canDrag(item)) {
                    dragStartPos = pos
                    itemTouchHelper.startDrag(viewHolder)
                }
            }
            true
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
        fun getNoteSwipeAction(direction: SwipeDirection): StatusChangeAction

        /** Called when a [NoteItem] at [pos] is swiped. */
        fun onNoteSwiped(item: NoteItem, pos: Int, direction: SwipeDirection)

        /** Called when a note is dragged [from] a position [to] another. */
        fun onNoteSwapped(item: NoteItem, from: Int, to: Int)

        /** Called when note dragging ends. */
        fun onNoteDragStart()

        /** Called when note dragging ends. */
        fun onNoteDragEnd()

        /** Whether a note item can be dragged or not. */
        fun canDrag(item: NoteItem): Boolean

        /** Whether strikethrough should be added to checked items or not. */
        val strikethroughCheckedItems: Boolean
    }
}
