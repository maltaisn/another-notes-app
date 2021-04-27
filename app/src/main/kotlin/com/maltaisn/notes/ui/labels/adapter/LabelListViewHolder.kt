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

package com.maltaisn.notes.ui.labels.adapter

import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.ItemLabelBinding

class LabelViewHolder(val binding: ItemLabelBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: LabelListItem, adapter: LabelAdapter) {
        binding.labelTxv.text = item.label.name

        val view = binding.root

        if (adapter.callback.shouldHighlightCheckedItems) {
            view.isActivated = item.checked
        } else {
            binding.labelImv.setImageResource(if (item.checked) {
                R.drawable.ic_label
            } else {
                R.drawable.ic_label_outline
            })
        }

        view.setOnClickListener {
            adapter.callback.onLabelItemClicked(item, adapterPosition)
        }
        view.setOnLongClickListener {
            adapter.callback.onLabelItemLongClicked(item, adapterPosition)
            true
        }
    }
}
