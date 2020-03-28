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

import android.view.KeyEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.BulletTextWatcher


class EditTitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleEdt = itemView as EditText

    fun bind(item: EditTitleItem) {
        titleEdt.setText(item.title)
        item.title = titleEdt.text
    }
}

class EditContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val contentEdt = itemView as EditText

    init {
        contentEdt.addTextChangedListener(BulletTextWatcher())
    }

    fun bind(item: EditContentItem) {
        // Change note content to the EditText editable text so view model can change it.
        contentEdt.setText(item.content)
        item.content = contentEdt.text
    }
}

class EditItemViewHolder(itemView: View, callback: EditAdapter.Callback) :
        RecyclerView.ViewHolder(itemView) {

    val dragImv: ImageView = itemView.findViewById(R.id.imv_item_drag)
    private val itemCheck: CheckBox = itemView.findViewById(R.id.chk_item)
    private val itemEdt: EditText = itemView.findViewById(R.id.edt_item)
    private val deleteImv: ImageView = itemView.findViewById(R.id.imv_item_delete)

    private lateinit var item: EditItemItem

    init {
        itemCheck.setOnCheckedChangeListener { _, isChecked ->
            item.checked = isChecked
        }

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
        itemEdt.setText(item.content)
        item.content = itemEdt.text

        itemCheck.isChecked = item.checked
    }

    fun setFocus(pos: Int) {
        itemEdt.requestFocus()
        itemEdt.setSelection(pos)
    }

    fun clearFocus() {
        itemEdt.clearFocus()
    }
}

class EditItemAddViewHolder(itemView: View, callback: EditAdapter.Callback) :
        RecyclerView.ViewHolder(itemView) {

    init {
        itemView.setOnClickListener {
            callback.onNoteItemAddClicked()
        }
    }
}
