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

package com.maltaisn.notes.ui.sort

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogSortBinding
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.model.SortSettings
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import debugCheck
import javax.inject.Inject
import javax.inject.Provider

class SortDialog : DialogFragment() {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var viewModelProvider: Provider<SortViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val binding = DialogSortBinding.inflate(layoutInflater, null, false)

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setTitle(R.string.sort_title)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val field = when (binding.sortFieldRadioGroup.checkedRadioButtonId) {
                    R.id.sort_field_added_radio -> SortField.ADDED_DATE
                    R.id.sort_field_modified_radio -> SortField.MODIFIED_DATE
                    R.id.sort_field_title_radio -> SortField.TITLE
                    else -> SortField.MODIFIED_DATE  // should not happen
                }
                val direction = when (binding.sortDirectionRadioGroup.checkedRadioButtonId) {
                    R.id.sort_direction_asc_radio -> SortDirection.ASCENDING
                    R.id.sort_direction_desc_radio -> SortDirection.DESCENDING
                    else -> SortDirection.DESCENDING  // should not happen
                }
                sharedViewModel.changeSortSettings(SortSettings(field, direction))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        setupViewModelObservers(binding)

        if (savedInstanceState == null) {
            viewModel.start()
        }

        return dialog
    }

    private fun setupViewModelObservers(binding: DialogSortBinding) {
        // Using `this` as lifecycle owner, cannot show dialog twice with same instance to avoid double observation.
        debugCheck(!viewModel.sortField.hasObservers()) { "Dialog was shown twice with same instance." }

        viewModel.sortField.observeEvent(this) { field ->
            when (field) {
                SortField.ADDED_DATE -> binding.sortFieldAddedRadio
                SortField.MODIFIED_DATE -> binding.sortFieldModifiedRadio
                SortField.TITLE -> binding.sortFieldTitleRadio
            }.isChecked = true
        }

        viewModel.sortDirection.observeEvent(this) { direction ->
            when (direction) {
                SortDirection.ASCENDING -> binding.sortDirectionAscRadio
                SortDirection.DESCENDING -> binding.sortDirectionDescRadio
            }.isChecked = true
        }
    }
}
