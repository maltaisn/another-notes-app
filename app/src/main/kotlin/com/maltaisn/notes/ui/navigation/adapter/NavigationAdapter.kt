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

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.maltaisn.notes.sync.databinding.ItemNavigationDividerBinding
import com.maltaisn.notes.sync.databinding.ItemNavigationHeaderBinding
import com.maltaisn.notes.sync.databinding.ItemNavigationItemBinding
import com.maltaisn.notes.sync.databinding.ItemNavigationTopBinding

class NavigationAdapter(
    val context: Context,
    val callback: Callback,
) : ListAdapter<NavigationItem, NavigationViewHolder>(NavigationListDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.TOP.ordinal -> NavigationTopViewHolder(ItemNavigationTopBinding
                .inflate(inflater, parent, false))
            ViewType.ITEM.ordinal -> NavigationItemViewHolder(ItemNavigationItemBinding
                .inflate(inflater, parent, false))
            ViewType.HEADER.ordinal -> NavigationHeaderViewHolder(ItemNavigationHeaderBinding
                .inflate(inflater, parent, false))
            ViewType.DIVIDER.ordinal -> NavigationDividerViewHolder(ItemNavigationDividerBinding
                .inflate(inflater, parent, false))
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: NavigationViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is NavigationItemViewHolder -> holder.bind(item as NavigationDestinationItem, callback)
            is NavigationHeaderViewHolder -> holder.bind(item as NavigationHeaderItem, callback)
            else -> Unit  // no data to bind (top, divider)
        }
    }

    override fun getItemId(position: Int) = getItem(position).id

    override fun getItemViewType(position: Int) = getItem(position).type.ordinal

    enum class ViewType {
        TOP,
        ITEM,
        HEADER,
        DIVIDER,
    }

    interface Callback {
        /** Called when a label [item] at [pos] is clicked. */
        fun onNavigationDestinationItemClicked(item: NavigationDestinationItem, pos: Int)

        /** Called when an action button on a header [item] is clicked. */
        fun onHeaderActionButtonClicked(item: NavigationHeaderItem)
    }
}
