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

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.strikethroughText
import com.maltaisn.notes.sync.BuildConfig
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.ItemHeaderBinding
import com.maltaisn.notes.sync.databinding.ItemMessageBinding
import com.maltaisn.notes.sync.databinding.ItemNoteListBinding
import com.maltaisn.notes.sync.databinding.ItemNoteListItemBinding
import com.maltaisn.notes.sync.databinding.ItemNoteTextBinding
import com.maltaisn.notes.ui.note.HighlightHelper
import com.maltaisn.notes.utils.RelativeDateFormatter
import java.text.DateFormat
import kotlin.math.min

/**
 * Maximum number of days in the past or the future for which
 * the reminder date is displayed in relative format.
 */
private const val MAXIMUM_RELATIVE_DATE_DAYS = 6

abstract class NoteViewHolder(itemView: View) :
    RecyclerView.ViewHolder(itemView) {

    private val dateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }

    protected abstract val cardView: MaterialCardView
    protected abstract val titleTxv: TextView
    protected abstract val reminderChip: Chip
    protected abstract val actionBtn: MaterialButton

    open fun bind(adapter: NoteAdapter, item: NoteItem) {
        val note = item.note

        var title = note.title
        if (BuildConfig.DEBUG) {
            title += " (${note.id})"
        }

        titleTxv.text = HighlightHelper.getHighlightedText(title, item.titleHighlights,
            adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
        titleTxv.isVisible = title.isNotBlank()

        cardView.isChecked = item.checked
        cardView.setOnClickListener {
            adapter.callback.onNoteItemClicked(item, adapterPosition)
        }
        cardView.setOnLongClickListener {
            adapter.callback.onNoteItemLongClicked(item, adapterPosition)
            true
        }

        reminderChip.isVisible = note.reminder != null
        if (note.reminder != null) {
            reminderChip.text = dateFormatter.format(note.reminder.next.time,
                System.currentTimeMillis(), MAXIMUM_RELATIVE_DATE_DAYS)
            reminderChip.strikethroughText = note.reminder.done
            reminderChip.isActivated = !note.reminder.done
            reminderChip.setChipIconResource(if (note.reminder.recurrence != null)
                R.drawable.ic_repeat else R.drawable.ic_alarm)
        }

        val bottomPadding: Int
        if (item.showMarkAsDone && !item.checked) {
            actionBtn.isVisible = true
            actionBtn.setIconResource(R.drawable.ic_check)
            actionBtn.setText(R.string.action_mark_as_done)
            actionBtn.setOnClickListener {
                adapter.callback.onNoteActionButtonClicked(item, adapterPosition)
            }
            bottomPadding = R.dimen.note_bottom_padding_with_action
        } else {
            actionBtn.isVisible = false
            bottomPadding = R.dimen.note_bottom_padding_no_action
        }
        cardView.setContentPadding(0, 0, 0,
            cardView.context.resources.getDimensionPixelSize(bottomPadding))
    }
}

class TextNoteViewHolder(private val binding: ItemNoteTextBinding) :
    NoteViewHolder(binding.root) {

    override val cardView = binding.cardView
    override val titleTxv = binding.titleTxv
    override val reminderChip = binding.reminderChip
    override val actionBtn = binding.actionBtn

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.TEXT)

        val contentTxv = binding.contentTxv
        contentTxv.isVisible = item.note.content.isNotBlank()
        contentTxv.text = HighlightHelper.getHighlightedText(item.note.content, item.contentHighlights,
            adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
        contentTxv.maxLines = adapter.getMaximumPreviewLines(NoteType.TEXT)
    }
}

class ListNoteViewHolder(private val binding: ItemNoteListBinding) : NoteViewHolder(binding.root) {

    override val cardView = binding.cardView
    override val titleTxv = binding.titleTxv
    override val reminderChip = binding.reminderChip
    override val actionBtn = binding.actionBtn

    private val itemViewHolders = mutableListOf<ListNoteItemViewHolder>()

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.LIST)
        require(itemViewHolders.isEmpty())

        val noteItems = item.note.listItems

        val itemsLayout = binding.itemsLayout
        itemsLayout.isVisible = noteItems.isNotEmpty()

        // Add the first few items in list note using view holders in pool.
        val maxItems = adapter.getMaximumPreviewLines(NoteType.LIST)
        val itemHighlights = HighlightHelper.splitListNoteHighlightsByItem(noteItems, item.contentHighlights)
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
        // Free view holders used by the item.
        val viewHolders = itemViewHolders.toList()
        binding.itemsLayout.removeViews(0, binding.itemsLayout.childCount - 1)
        itemViewHolders.clear()

        return viewHolders
    }
}

class MessageViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: MessageItem, adapter: NoteAdapter) {
        binding.messageTxv.text = adapter.context.getString(item.message, *item.args.toTypedArray())
        binding.closeImv.setOnClickListener {
            adapter.callback.onMessageItemDismissed(item, adapterPosition)
            adapter.notifyItemRemoved(adapterPosition)
        }

        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

class HeaderViewHolder(private val binding: ItemHeaderBinding) : RecyclerView.ViewHolder(binding.root) {

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
        binding.contentTxv.apply {
            text = HighlightHelper.getHighlightedText(item.content, highlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor)
            strikethroughText = item.checked && adapter.callback.strikethroughCheckedItems
        }

        binding.checkboxImv.setImageResource(if (item.checked) {
            R.drawable.ic_checkbox_on
        } else {
            R.drawable.ic_checkbox_off
        })
    }
}
