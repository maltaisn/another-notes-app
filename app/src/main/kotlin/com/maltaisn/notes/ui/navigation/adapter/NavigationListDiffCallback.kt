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

import androidx.recyclerview.widget.DiffUtil

class NavigationListDiffCallback : DiffUtil.ItemCallback<NavigationItem>() {

    override fun areItemsTheSame(old: NavigationItem, new: NavigationItem) = (old.id == new.id)

    override fun areContentsTheSame(old: NavigationItem, new: NavigationItem): Boolean {
        if (old.type != new.type) {
            // Should never happen since items of different types can't have the same ID.
            return false
        }

        return when (new) {
            is NavigationDestinationItem -> {
                old as NavigationDestinationItem
                new.checked == old.checked &&
                        new.iconRes == old.iconRes &&
                        new.titleRes == old.titleRes &&
                        new.title == old.title
            }
            is NavigationHeaderItem -> {
                old as NavigationHeaderItem
                new.actionBtnTextRes == old.actionBtnTextRes &&
                        new.titleRes == old.titleRes
            }
            else -> true  // divider, top
        }
    }

}
