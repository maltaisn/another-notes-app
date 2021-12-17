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
import android.graphics.Canvas
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.note.SwipeAction
import com.maltaisn.notes.ui.note.adapter.NoteAdapter.SwipeDirection
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Item touch helper callback for swiping items.
 */
class SwipeTouchHelperCallback(private val callback: NoteAdapter.Callback) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled() = false
    override fun isItemViewSwipeEnabled() = true

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = ITEM_SWIPE_THRESHOLD

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
        makeMovementFlags(0, if (viewHolder is NoteViewHolder<*>) {
            // Only allow swiping left and right
            var flags = 0
            if (callback.getNoteSwipeAction(SwipeDirection.LEFT) != SwipeAction.NONE) {
                flags = flags or ItemTouchHelper.LEFT
            }
            if (callback.getNoteSwipeAction(SwipeDirection.RIGHT) != SwipeAction.NONE) {
                flags = flags or ItemTouchHelper.RIGHT
            }
            flags
        } else {
            0
        })

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        viewHolder as NoteViewHolder<*>
        val cardView = viewHolder.cardView

        val direction = if (dX < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
        val swipeAction = callback.getNoteSwipeAction(direction)
        var dist = dX.absoluteValue
        if (dist < cardView.width * ITEM_SWIPE_LOCK) {
            dist = 0f
        }

        // Make the card progressively more transparent the farther it is dragged.
        cardView.alpha = (1 - dist / cardView.width * ITEM_SWIPE_OPACITY_FACTOR)
            .coerceAtLeast(ITEM_SWIPE_OPACITY_MIN)
        cardView.translationX = dist * dX.sign

        viewHolder.swipeImv.isInvisible = if (dist == 0f || swipeAction == SwipeAction.NONE) {
            true
        } else {
            updateSwipeImage(viewHolder, direction)
            false
        }
    }

    private fun updateSwipeImage(viewHolder: NoteViewHolder<*>, direction: SwipeDirection) {
        val swipeImv = viewHolder.swipeImv

        @SuppressLint("RtlHardcoded")
        val layoutGravity = Gravity.CENTER_VERTICAL or
                if (direction == SwipeDirection.LEFT) Gravity.RIGHT else Gravity.LEFT
        val layoutParams = swipeImv.layoutParams as FrameLayout.LayoutParams
        if (layoutParams.gravity != layoutGravity || swipeImv.isInvisible) {
            // Swipe layout changed direction or just started being visible
            layoutParams.gravity = layoutGravity
            swipeImv.requestLayout()

            viewHolder.swipeImv.setImageResource(when (callback.getNoteSwipeAction(direction)) {
                SwipeAction.ARCHIVE -> R.drawable.avd_archive
                SwipeAction.DELETE -> R.drawable.avd_delete
                else -> return // never happens
            })
            (viewHolder.swipeImv.drawable as? AnimatedVectorDrawable)?.start()
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        clearView(viewHolder)
    }

    private fun clearView(viewHolder: RecyclerView.ViewHolder) {
        viewHolder as NoteViewHolder<*>
        val cardView = viewHolder.cardView
        cardView.alpha = 1f
        cardView.translationX = 0f
        viewHolder.swipeImv.isInvisible = true
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        callback.onNoteSwiped(viewHolder.bindingAdapterPosition,
            if (direction == ItemTouchHelper.LEFT) SwipeDirection.LEFT else SwipeDirection.RIGHT)
    }

    companion object {
        /** The percentage of the width to be swiped for swipe action to work. */
        private const val ITEM_SWIPE_THRESHOLD = 0.6f

        /** Minimum drag distance in percentage of item width to show swipe animation. */
        private const val ITEM_SWIPE_LOCK = 0.075f

        /** Determines how fast opacity changes with swipe distance. */
        private const val ITEM_SWIPE_OPACITY_FACTOR = 0.7f

        /** Minimum item opacity when swiping. */
        private const val ITEM_SWIPE_OPACITY_MIN = 0.1f
    }
}
