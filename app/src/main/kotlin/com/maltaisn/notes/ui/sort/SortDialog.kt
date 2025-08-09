/*
 * Copyright 2025 Nicolas Maltais
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
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogSortBinding
import com.maltaisn.notes.databinding.ItemBottomSheetActionBinding
import com.maltaisn.notes.debugCheck
import com.maltaisn.notes.model.SortDirection
import com.maltaisn.notes.model.SortField
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.observeEvent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SortDialog : BottomSheetDialogFragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val viewModel: SortViewModel by viewModels()

    private var _binding: DialogSortBinding? = null
    private val binding get() = _binding!!

    private val actionItems = mutableMapOf<SortField, ItemBottomSheetActionBinding>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val dialog = BottomSheetDialog(context)

        // Fully expand the sheet by default
        val behavior = dialog.behavior
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        _binding = DialogSortBinding.inflate(layoutInflater, null, false)
        dialog.setContentView(binding.root)

        createActionItem(SortField.TITLE, R.string.sort_field_title)
        createActionItem(SortField.ADDED_DATE, R.string.sort_field_created)
        createActionItem(SortField.MODIFIED_DATE, R.string.sort_field_modified)

        setupViewModelObservers()

        viewModel.start()
        return dialog
    }

    private fun createActionItem(field: SortField, @StringRes title: Int) {
        val itemBinding = ItemBottomSheetActionBinding.inflate(layoutInflater, binding.contentLayout, true)
        itemBinding.titleTxv.setText(title)
        itemBinding.root.setOnClickListener {
            viewModel.changeSortField(field)
        }
        actionItems[field] = itemBinding
    }

    private fun setupViewModelObservers() {
        // Using `this` as lifecycle owner, cannot show dialog twice with same instance to avoid double observation.
        debugCheck(!viewModel.sortSettings.hasObservers()) { "Dialog was shown twice with same instance." }

        viewModel.sortSettings.observe(this) { settings ->
            for ((field, binding) in actionItems) {
                val selected = field == settings.field
                binding.root.isActivated = selected
                binding.iconImv.isInvisible = !selected
                binding.iconImv.setImageResource(when (settings.direction) {
                    SortDirection.ASCENDING -> R.drawable.ic_arrow_up
                    SortDirection.DESCENDING -> R.drawable.ic_arrow_down
                })
            }
        }

        viewModel.sortSettingsChange.observeEvent(this) { settings ->
            dismiss()
            sharedViewModel.changeSortSettings(settings)
        }
    }
}
