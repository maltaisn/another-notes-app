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

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.absoluteValue

/**
 * Item touch helper callback for swiping items.
 */
class SwipeTouchHelperCallback(private val callback: NoteAdapter.Callback) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled() = false
    override fun isItemViewSwipeEnabled() = callback.isNoteSwipeEnabled

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
        makeMovementFlags(0, if (viewHolder is NoteViewHolder) {
            ItemTouchHelper.START or ItemTouchHelper.END
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
        val view = viewHolder.itemView
        view.alpha = (1 - dX.absoluteValue / view.width * ITEM_SWIPE_OPACITY_FACTOR)
            .coerceAtLeast(ITEM_SWIPE_OPACITY_MIN)
        view.translationX = dX
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val view = viewHolder.itemView
        view.alpha = 1f
        view.translationX = 0f
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        callback.onNoteSwiped(viewHolder.adapterPosition)
    }

    companion object {
        /** Determines how fast opacity changes with swipe distance. */
        private const val ITEM_SWIPE_OPACITY_FACTOR = 0.7f

        /** Minimum item opacity when swiping. */
        private const val ITEM_SWIPE_OPACITY_MIN = 0.1f
    }
}
