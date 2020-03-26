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

import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R


class EditTitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleEdt = itemView as EditText

    fun bind(item: EditTitleItem) {
        titleEdt.setText(item.title, TextView.BufferType.EDITABLE)
        item.title = titleEdt.text
    }
}

class EditContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val contentEdt = itemView as TextView

    fun bind(item: EditContentItem) {
        contentEdt.setText(item.content, TextView.BufferType.EDITABLE)
        item.content = contentEdt.text
    }
}

class EditItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val dragImv: ImageView = itemView.findViewById(R.id.imv_item_drag)
    private val itemCheck: CheckBox = itemView.findViewById(R.id.chk_item)
    private val itemEdt: EditText = itemView.findViewById(R.id.edt_item)
    private val deleteImv: ImageView = itemView.findViewById(R.id.imv_item_delete)

    fun bind(item: EditItemItem) {
        itemEdt.setText(item.content, TextView.BufferType.EDITABLE)
        item.content = itemEdt.text

        itemCheck.isChecked = item.checked
        itemCheck.setOnCheckedChangeListener { _, isChecked ->
            item.checked = isChecked
        }

        itemEdt.addTextChangedListener { item.onChange(item, adapterPosition) }
        itemEdt.setOnFocusChangeListener { _, hasFocus ->
            deleteImv.isInvisible = !hasFocus
        }

        deleteImv.setOnClickListener { item.onDelete(adapterPosition) }
    }
}

class EditItemAddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(item: EditItemAddItem) {
        itemView.setOnClickListener { item.onClick() }
    }
}
