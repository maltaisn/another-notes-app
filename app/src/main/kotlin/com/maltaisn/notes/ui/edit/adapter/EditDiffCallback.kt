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

package com.maltaisn.notes.ui.edit.adapter

import androidx.recyclerview.widget.DiffUtil

class EditDiffCallback : DiffUtil.ItemCallback<EditListItem>() {

    override fun areItemsTheSame(old: EditListItem, new: EditListItem) = when {
        // Checked header item is recreated when count changes
        old is EditCheckedHeaderItem && new is EditCheckedHeaderItem -> true
        // Items are not recreated on a change so just check if they're the same object.
        else -> old === new
    }

    override fun areContentsTheSame(old: EditListItem, new: EditListItem) = when {
        old is EditCheckedHeaderItem && new is EditCheckedHeaderItem -> old.count == new.count
        // If this is called, then old === new, so content is necessarily the same.
        else -> true
    }
}
