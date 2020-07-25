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

import android.text.Editable
import android.text.InputFilter
import android.text.Spannable
import android.view.KeyEvent
import androidx.core.text.clearSpans
import androidx.core.view.isInvisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.sync.databinding.ItemEditContentBinding
import com.maltaisn.notes.sync.databinding.ItemEditItemAddBinding
import com.maltaisn.notes.sync.databinding.ItemEditItemBinding
import com.maltaisn.notes.sync.databinding.ItemEditTitleBinding
import com.maltaisn.notes.ui.edit.BulletTextWatcher

/**
 * Interface implemented by any item that can have its focus position changed.
 */
interface EditFocusableViewHolder {
    fun setFocus(pos: Int)
}

class EditTitleViewHolder(binding: ItemEditTitleBinding, callback: EditAdapter.Callback) :
        RecyclerView.ViewHolder(binding.root), EditFocusableViewHolder {

    private val titleEdt = binding.titleEdt

    init {
        titleEdt.setOnClickListener {
            callback.onNoteClickedToEdit()
        }
        titleEdt.filters = arrayOf(clearSpansInputFilter)
        titleEdt.setHorizontallyScrolling(false)
        titleEdt.maxLines = Integer.MAX_VALUE
    }

    fun bind(item: EditTitleItem) {
        titleEdt.isFocusable = item.editable
        titleEdt.isFocusableInTouchMode = item.editable

        // Set text and change to AndroidEditableText so view model can see changes to it.
        titleEdt.setText(item.title.text)
        item.title = AndroidEditableText(titleEdt.text)
    }

    override fun setFocus(pos: Int) {
        titleEdt.requestFocus()
        titleEdt.setSelection(pos)
    }
}

class EditContentViewHolder(binding: ItemEditContentBinding, callback: EditAdapter.Callback) :
        RecyclerView.ViewHolder(binding.root), EditFocusableViewHolder {

    private val contentEdt = binding.contentEdt

    init {
        contentEdt.filters = arrayOf(clearSpansInputFilter)
        contentEdt.addTextChangedListener(BulletTextWatcher())
        contentEdt.setOnClickListener {
            callback.onNoteClickedToEdit()
        }
    }

    fun bind(item: EditContentItem) {
        // Change note content to the EditText editable text so view model can change it.
        contentEdt.isFocusable = item.editable
        contentEdt.isFocusableInTouchMode = item.editable

        // Set text and change to AndroidEditableText so view model can see changes to it.
        contentEdt.setText(item.content.text)
        item.content = AndroidEditableText(contentEdt.text)
    }

    override fun setFocus(pos: Int) {
        contentEdt.requestFocus()
        contentEdt.setSelection(pos)
    }
}

class EditItemViewHolder(val binding: ItemEditItemBinding, callback: EditAdapter.Callback) :
        RecyclerView.ViewHolder(binding.root), EditFocusableViewHolder {

    private val itemCheck = binding.itemChk
    private val itemEdt = binding.contentEdt
    private val deleteImv = binding.deleteImv

    private lateinit var item: EditItemItem

    init {
        itemCheck.setOnCheckedChangeListener { _, isChecked ->
            item.checked = isChecked
        }

        itemEdt.filters = arrayOf(clearSpansInputFilter)

        itemEdt.doOnTextChanged { _, _, _, count ->
            // This is used to detect when user enters line breaks into the input, so the
            // item can be split into multiple items. When user enters a single line break,
            // selection is set at the beginning of new item. On paste, i.e. when more than one
            // character is entered, selection is set at the end of last new item.
            callback.onNoteItemChanged(item, adapterPosition, count > 1)
        }
        itemEdt.setOnFocusChangeListener { _, hasFocus ->
            // Only show delete icon for currently focused item.
            deleteImv.isInvisible = !hasFocus
        }
        itemEdt.setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN
                    && event.keyCode == KeyEvent.KEYCODE_DEL
                    && itemEdt.selectionStart == itemEdt.selectionEnd
                    && itemEdt.selectionStart == 0) {
                // If user presses backspace at the start of an item, current item
                // will be merged with previous.
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    callback.onNoteItemBackspacePressed(item, pos)
                }
            }
            false
        }
        itemEdt.setOnClickListener {
            callback.onNoteClickedToEdit()
        }

        deleteImv.setOnClickListener {
            val pos = adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                callback.onNoteItemDeleteClicked(pos)
            }
        }
    }

    fun bind(item: EditItemItem) {
        this.item = item

        // Change item content to the EditText editable text so view model can change it.
        itemEdt.isFocusable = item.editable
        itemEdt.isFocusableInTouchMode = item.editable

        // Set text and change to AndroidEditableText so view model can see changes to it.
        itemEdt.setText(item.content.text)
        item.content = AndroidEditableText(itemEdt.text)

        itemCheck.isChecked = item.checked
        itemCheck.isEnabled = item.editable
    }

    override fun setFocus(pos: Int) {
        itemEdt.requestFocus()
        itemEdt.setSelection(pos)
    }

    fun clearFocus() {
        itemEdt.clearFocus()
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

private class AndroidEditableText(override val text: Editable) : EditableText {

    override fun append(text: CharSequence) {
        this.text.append(text)
    }

    override fun replaceAll(text: CharSequence) {
        this.text.replace(0, this.text.length, text)
    }
}

private val clearSpansInputFilter = InputFilter { source, start, end, _, _, _ ->
    // Pasted text can often have foreign spans, for example when copying text from the browser.
    // This filter removes all of them.
    val filtered = source.subSequence(start, end)
    if (filtered is Spannable) {
        filtered.clearSpans()
    }
    filtered
}
