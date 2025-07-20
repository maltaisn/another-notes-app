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

package com.maltaisn.notes.ui.labels

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogLabelEditBinding
import com.maltaisn.notes.debugCheck
import com.maltaisn.notes.hideCursorInAllViews
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.setTitleIfEnoughSpace
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.observeEvent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LabelEditDialog : DialogFragment() {

    val viewModel: LabelEditViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by hiltNavGraphViewModels(R.id.nav_graph_main)

    private val args: LabelEditDialogArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val binding = DialogLabelEditBinding.inflate(layoutInflater, null, false)

        // Using `this` as lifecycle owner, cannot show dialog twice with same instance to avoid double observation.
        debugCheck(!viewModel.setLabelEvent.hasObservers()) { "Dialog was shown twice with same instance." }

        val nameInput = binding.labelInput
        val nameInputLayout = binding.labelInputLayout
        val hiddenCheck = binding.labelHiddenChk

        val builder = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                viewModel.addLabel()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setTitleIfEnoughSpace(if (args.labelId == Label.NO_ID) {
                R.string.label_create
            } else {
                R.string.label_edit
            })

        val dialog = builder.create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.setOnShowListener {
            val okBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            viewModel.nameError.observe(this) { error ->
                okBtn.isEnabled = error == LabelEditViewModel.Error.NONE
                nameInputLayout.isErrorEnabled = error == LabelEditViewModel.Error.DUPLICATE
                if (error == LabelEditViewModel.Error.DUPLICATE) {
                    nameInputLayout.error = getString(R.string.label_already_exists)
                }
            }
        }

        hiddenCheck.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onHiddenChanged(isChecked)
        }

        nameInput.isCursorVisible = true
        nameInput.doAfterTextChanged {
            viewModel.onNameChanged(it?.toString() ?: "")
        }
        nameInput.requestFocus()
        viewModel.setLabelEvent.observeEvent(this) { label ->
            nameInput.setText(label.name)
            nameInput.setSelection(label.name.length)  // put cursor at the end
            hiddenCheck.isChecked = label.hidden
        }

        viewModel.labelAddEvent.observeEvent(this, sharedViewModel::onLabelAdd)

        viewModel.start(args.labelId)

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideCursorInAllViews()
    }
}
