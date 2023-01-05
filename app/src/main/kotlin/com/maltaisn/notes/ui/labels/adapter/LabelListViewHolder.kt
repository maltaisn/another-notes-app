/*
 * Copyright 2023 Nicolas Maltais
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

import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.BuildConfig
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.ItemLabelBinding

class LabelListViewHolder(val binding: ItemLabelBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: LabelListItem, adapter: LabelAdapter) {
        var name = item.label.name
        if (BuildConfig.ENABLE_DEBUG_FEATURES) {
            name += " (${item.label.id})"
        }

        binding.labelTxv.text = name
        binding.hiddenImv.isInvisible = !item.label.hidden

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

        binding.labelImv.setOnClickListener {
            adapter.callback.onLabelItemIconClicked(item, bindingAdapterPosition)
        }
        view.setOnClickListener {
            adapter.callback.onLabelItemClicked(item, bindingAdapterPosition)
        }
        view.setOnLongClickListener {
            adapter.callback.onLabelItemLongClicked(item, bindingAdapterPosition)
            true
        }
    }
}
