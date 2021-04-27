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

package com.maltaisn.notes.ui.labels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.labels.adapter.LabelAdapter
import com.maltaisn.notes.ui.labels.adapter.LabelListItem
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LabelViewModel @AssistedInject constructor(
    private val labelsRepository: LabelsRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel(), LabelAdapter.Callback {

    private val _labelItems = MutableLiveData<List<LabelListItem>>()
    val labelItems: LiveData<List<LabelListItem>>
        get() = _labelItems

    private val _labelSelection = MutableLiveData<Int>()
    val labelSelection: LiveData<Int>
        get() = _labelSelection

    private val _placeholderShown = MutableLiveData<Boolean>()
    val placeholderShown: LiveData<Boolean>
        get() = _placeholderShown

    private var listItems = mutableListOf<LabelListItem>()
        set(value) {
            field = value
            _labelItems.value = value

            // Update selected notes.
            val selectedBefore = selectedLabels.size
            selectedLabels.clear()
            for (item in value) {
                if (item.checked) {
                    selectedLabels += item.label
                }
            }

            // Update placeholder visibility
            _placeholderShown.value = value.isEmpty()

            _labelSelection.value = selectedLabels.size
            if (selectedLabels.size != selectedBefore) {
                saveLabelSelectionState()
            }
        }

    private val selectedLabels = mutableSetOf<Label>()

    init {
        viewModelScope.launch {
            // Restore label selection
            val selectedIds = savedStateHandle.get<List<Long>>(KEY_SELECTED_IDS).orEmpty()
            selectedLabels += selectedIds.mapNotNull { labelsRepository.getLabelById(it) }

            // Initialize label list
            labelsRepository.getAllLabels().collect { labels ->
                listItems = labels.mapTo(mutableListOf()) { label ->
                    LabelListItem(label.id, label, label in selectedLabels)
                }
            }
        }
    }

    fun clearSelection() {
        setAllSelected(false)
    }

    fun selectAll() {
        setAllSelected(true)
    }

    /** Set the selected state of all notes to [selected]. */
    private fun setAllSelected(selected: Boolean) {
        if (!selected && selectedLabels.isEmpty() ||
            selected && selectedLabels.size == listItems.size
        ) {
            // Already all unselected or all selected.
            return
        }

        changeListItems {
            for ((i, item) in it.withIndex()) {
                if (item.checked != selected) {
                    it[i] = item.copy(checked = selected)
                }
            }
        }
    }

    override fun onLabelItemClicked(item: LabelListItem, pos: Int) {
        if (selectedLabels.isEmpty()) {
            // TODO edit label name
        } else {
            toggleItemChecked(item, pos)
        }
    }

    override fun onLabelItemLongClicked(item: LabelListItem, pos: Int) {
        toggleItemChecked(item, pos)
    }

    private fun toggleItemChecked(item: LabelListItem, pos: Int) {
        // Set the item as checked and update the list.
        changeListItems {
            it[pos] = item.copy(checked = !item.checked)
        }
    }

    private inline fun changeListItems(change: (MutableList<LabelListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    /** Save [selectedLabels] to [savedStateHandle]. */
    private fun saveLabelSelectionState() {
        savedStateHandle.set(KEY_SELECTED_IDS, selectedLabels.mapTo(ArrayList()) { it.id })
    }

    @AssistedInject.Factory
    interface Factory : AssistedSavedStateViewModelFactory<LabelViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): LabelViewModel
    }

    companion object {
        private const val KEY_SELECTED_IDS = "selected_ids"
    }

}
