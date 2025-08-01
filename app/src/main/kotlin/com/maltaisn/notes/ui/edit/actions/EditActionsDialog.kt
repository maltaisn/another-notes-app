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

package com.maltaisn.notes.ui.edit.actions

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogEditActionsBinding
import com.maltaisn.notes.databinding.ItemEditActionBinding
import com.maltaisn.notes.ui.edit.EditViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditActionsDialog : BottomSheetDialogFragment() {

    private val viewModel: EditViewModel by hiltNavGraphViewModels(R.id.fragment_edit)

    private var _binding: DialogEditActionsBinding? = null
    private val binding get() = _binding!!

    private var itemsCreated = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val dialog = BottomSheetDialog(context)

        // Fully expand the sheet by default
        val behavior = dialog.behavior
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val inflater = LayoutInflater.from(context)
        _binding = DialogEditActionsBinding.inflate(inflater, null, false)
        dialog.setContentView(binding.root)

        setupViewModelObservers()

        return dialog
    }

    private fun setupViewModelObservers() {
        viewModel.editActionsAvailability.observe(this, ::createActionItems)
    }

    private fun createActionItems(visibility: EditActionsAvailability) {
        if (itemsCreated) {
            // If a change occurs in action visibility, it's when the dialog is being dismissed,
            // so don't update the items.
            return
        }

        val context = requireContext()
        val inToolbarMax = context.resources.getInteger(R.integer.edit_actions_in_toolbar)
        val editActions = visibility.createActions(context)
        createDialogItemsForEditActions(inToolbarMax, editActions, ::createActionItem)
        itemsCreated = true
    }

    private fun createActionItem(action: EditAction) {
        val itemBinding = ItemEditActionBinding.inflate(requireActivity().layoutInflater, binding.contentLayout, true)
        itemBinding.titleTxv.setText(action.title)
        itemBinding.iconImv.setImageResource(action.icon)
        itemBinding.root.setOnClickListener {
            // Dismiss *first*, so that the navigation controller back stack is correct.
            dismiss()

            action.action(viewModel)
        }
        itemBinding.root.isEnabled = action.available == EditActionAvailability.AVAILABLE
    }
}

fun createDialogItemsForEditActions(
    maxCountInToolbar: Int,
    editActions: List<EditAction>,
    callback: (EditAction) -> Unit,
) {
    var inToolbarCount = 0
    for (action in editActions) {
        if (action.available != EditActionAvailability.HIDDEN) {
            if (!action.showInToolbar || inToolbarCount >= maxCountInToolbar) {
                callback(action)
            } else {
                inToolbarCount++
            }
        }
    }
}