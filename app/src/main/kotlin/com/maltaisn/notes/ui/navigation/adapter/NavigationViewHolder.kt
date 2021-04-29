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

package com.maltaisn.notes.ui.navigation.adapter

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.sync.databinding.ItemNavigationDividerBinding
import com.maltaisn.notes.sync.databinding.ItemNavigationHeaderBinding
import com.maltaisn.notes.sync.databinding.ItemNavigationItemBinding
import com.maltaisn.notes.sync.databinding.ItemNavigationTopBinding

sealed class NavigationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

class NavigationTopViewHolder(binding: ItemNavigationTopBinding) :
        NavigationViewHolder(binding.root)

class NavigationItemViewHolder(val binding: ItemNavigationItemBinding) :
    NavigationViewHolder(binding.root) {

    fun bind(item: NavigationDestinationItem, callback: NavigationAdapter.Callback) {
        binding.itemImv.setImageResource(item.iconRes)
        if (item.titleRes != 0) {
            binding.itemTxv.setText(item.titleRes)
        } else {
            binding.itemTxv.text = item.title
        }

        binding.itemLayout.isActivated = item.checked
        // (activated drawable state is propagated to children views that use a color state list)
        binding.itemLayout.setOnClickListener {
            callback.onNavigationDestinationItemClicked(item, adapterPosition)
        }
    }
}

class NavigationHeaderViewHolder(val binding: ItemNavigationHeaderBinding) :
    NavigationViewHolder(binding.root) {

    fun bind(item: NavigationHeaderItem, callback: NavigationAdapter.Callback) {
        binding.titleTxv.setText(item.titleRes)

        val actionBtn = binding.actionBtn
        if (item.actionBtnTextRes != 0) {
            actionBtn.isVisible = true
            actionBtn.setText(item.actionBtnTextRes)
            actionBtn.setOnClickListener {
                callback.onHeaderActionButtonClicked(item)
            }
        } else {
            actionBtn.isVisible = false
        }
    }
}

class NavigationDividerViewHolder(val binding: ItemNavigationDividerBinding) :
        NavigationViewHolder(binding.root)
