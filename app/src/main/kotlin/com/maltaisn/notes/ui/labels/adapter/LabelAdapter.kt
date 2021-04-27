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

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.maltaisn.notes.sync.databinding.ItemLabelBinding

class LabelAdapter(
    val context: Context,
    val callback: Callback,
) : ListAdapter<LabelListItem, LabelViewHolder>(LabelListDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return LabelViewHolder(ItemLabelBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: LabelViewHolder, position: Int) {
        holder.bind(getItem(position), this)
    }

    override fun getItemId(position: Int) = getItem(position).id

    interface Callback {
        val shouldHighlightCheckedItems: Boolean

        /** Called when a label [item] at [pos] is clicked. */
        fun onLabelItemClicked(item: LabelListItem, pos: Int)

        /** Called when a label [item] at [pos] is long-clicked. */
        fun onLabelItemLongClicked(item: LabelListItem, pos: Int)
    }
}
