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

package com.maltaisn.notes.ui.edit.adapter

import android.text.Editable
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.ItemEditContentBinding
import com.maltaisn.notes.databinding.ItemEditDateBinding
import com.maltaisn.notes.databinding.ItemEditHeaderBinding
import com.maltaisn.notes.databinding.ItemEditItemAddBinding
import com.maltaisn.notes.databinding.ItemEditItemBinding
import com.maltaisn.notes.databinding.ItemEditLabelsBinding
import com.maltaisn.notes.databinding.ItemEditTitleBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.model.DefaultPrefsManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.showKeyboard
import com.maltaisn.notes.strikethroughText
import com.maltaisn.notes.ui.edit.BulletTextWatcher
import com.maltaisn.notes.ui.edit.DefaultEditableText
import com.maltaisn.notes.utils.RelativeDateFormatter
import java.text.DateFormat

/**
 * Abstract class for items that can be focused and have their focus position changed.
 */
sealed class EditFocusableViewHolder<T : EditTextItem>(root: View) :
    RecyclerView.ViewHolder(root) {

    abstract val editText: EditEditText
    var item: T? = null

    fun init(callback: EditAdapter.Callback) {
        editText.setOnClickListener {
            callback.onNoteClickedToEdit()
        }
        editText.setOnKeyListener { _, _, event ->
            val pos = bindingAdapterPosition
            val isCursorAtStart = editText.selectionStart == 0 && editText.selectionStart == editText.selectionEnd
            if (pos != RecyclerView.NO_POSITION && isCursorAtStart &&
                event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL
            ) {
                callback.onNoteItemBackspacePressed(pos)
            }
            false
        }
        editText.onTextChangedForUndoListener = { start, end, oldText, newText ->
            callback.onTextChanged(bindingAdapterPosition, start, end, oldText, newText)
        }
        editText.setEditableFactory(object : Editable.Factory() {
            override fun newEditable(source: CharSequence?): Editable? {
                return (item?.text as DefaultEditableText?)?.text ?: super.newEditable(source)
            }
        })
        editText.onLinkClickListener = callback::onLinkClickedInNote

        editText.setAutoTextSize(callback.textSize)
    }

    fun setFocus(pos: Int) {
        editText.requestFocus()
        editText.setSelection(pos)
        editText.showKeyboard()
    }

    open fun bind(item: T) {
        this.item = item
        editText.isFocusable = item.editable
        editText.isFocusableInTouchMode = item.editable
        editText.setTextIgnoringUndo(item.text.text)
    }
}

class EditDateViewHolder(binding: ItemEditDateBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val dateEdt = binding.dateEdt

    private val dateFormatter = RelativeDateFormatter(dateEdt.resources) { date ->
        DateUtils.formatDateTime(dateEdt.context, date, DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
    }

    fun bind(item: EditDateItem) {
        dateEdt.text = dateFormatter.format(item.date, System.currentTimeMillis(),
            DefaultPrefsManager.MAXIMUM_RELATIVE_DATE_DAYS)
    }
}

class EditTitleViewHolder(binding: ItemEditTitleBinding, callback: EditAdapter.Callback) :
    EditFocusableViewHolder<EditTitleItem>(binding.root) {

    override val editText = binding.titleEdt

    init {
        init(callback)
        editText.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_NEXT) {
                callback.onNoteTitleEnterPressed()
            }
            false
        }
        editText.setHorizontallyScrolling(false)  // Fails when set as an attribute for some reason
        editText.maxLines = Integer.MAX_VALUE
    }
}

class EditContentViewHolder(binding: ItemEditContentBinding, callback: EditAdapter.Callback) :
    EditFocusableViewHolder<EditContentItem>(binding.root) {

    override val editText = binding.contentEdt

    init {
        init(callback)
        editText.addTextChangedListener(BulletTextWatcher())
    }
}

class EditItemViewHolder(binding: ItemEditItemBinding, callback: EditAdapter.Callback) :
    EditFocusableViewHolder<EditItemItem>(binding.root) {

    override val editText = binding.contentEdt
    val dragImv = binding.dragImv
    private val itemCheck = binding.itemChk
    private val deleteImv = binding.deleteImv

    val isChecked: Boolean
        get() = itemCheck.isChecked

    private var ignoreChecks = false

    init {
        init(callback)
        itemCheck.setOnCheckedChangeListener { _, isChecked ->
            editText.strikethroughText = isChecked && callback.strikethroughCheckedItems
            editText.isActivated = !isChecked // Controls text color selector.
            dragImv.isInvisible = isChecked && callback.moveCheckedToBottom

            if (ignoreChecks) return@setOnCheckedChangeListener

            editText.clearFocus()
            editText.hideKeyboard()

            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onNoteItemCheckChanged(pos, isChecked)
            }
        }

        editText.setOnFocusChangeListener { _, hasFocus ->
            // Only show delete icon for currently focused item.
            deleteImv.isInvisible = !hasFocus
        }
        deleteImv.setOnClickListener {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onNoteItemDeleteClicked(pos)
            }
        }
    }

    override fun bind(item: EditItemItem) {
        super.bind(item)

        itemCheck.isEnabled = item.editable
        editText.isActivated = !item.checked
        ignoreChecks = true
        itemCheck.isChecked = item.checked
        ignoreChecks = false
    }

    fun clearFocus() {
        editText.clearFocus()
    }
}

class EditItemAddViewHolder(binding: ItemEditItemAddBinding, callback: EditAdapter.Callback) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        itemView.setOnClickListener {
            callback.onNoteItemAddClicked()
        }
    }
}

class EditHeaderViewHolder(binding: ItemEditHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val titleTxv = binding.titleTxv

    fun bind(item: EditCheckedHeaderItem) {
        titleTxv.text = titleTxv.context.resources.getQuantityString(
            R.plurals.edit_checked_items, item.count, item.count)
    }
}

class EditItemLabelsViewHolder(binding: ItemEditLabelsBinding, callback: EditAdapter.Callback) :
    RecyclerView.ViewHolder(binding.root) {

    private val chipGroup = binding.chipGroup
    private val labelClickListener = View.OnClickListener {
        callback.onNoteLabelClicked()
    }
    private val reminderClickListener = View.OnClickListener {
        callback.onNoteReminderClicked()
    }

    private val reminderDateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }

    fun bind(item: EditChipsItem) {
        val layoutInflater = LayoutInflater.from(chipGroup.context)
        chipGroup.removeAllViews()
        for (chip in item.chips) {
            when (chip) {
                is Label -> {
                    val view = layoutInflater.inflate(R.layout.view_edit_chip_label,
                        chipGroup,
                        false) as Chip
                    chipGroup.addView(view)
                    view.text = chip.name
                    view.setOnClickListener(labelClickListener)
                }

                is Reminder -> {
                    val view = layoutInflater.inflate(R.layout.view_edit_chip_reminder,
                        chipGroup,
                        false) as Chip
                    chipGroup.addView(view)
                    view.text = reminderDateFormatter.format(chip.next.time,
                        System.currentTimeMillis(), DefaultPrefsManager.MAXIMUM_RELATIVE_DATE_DAYS)
                    view.strikethroughText = chip.done
                    view.isActivated = !chip.done
                    view.setChipIconResource(if (chip.recurrence != null) R.drawable.ic_repeat else R.drawable.ic_alarm)
                    view.setOnClickListener(reminderClickListener)
                }

                else -> error("Unknown chip type")
            }
        }
    }
}
