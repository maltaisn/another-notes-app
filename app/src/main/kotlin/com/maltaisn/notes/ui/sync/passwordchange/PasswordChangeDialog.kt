/*
 * Copyright 2020 Nicolas Maltais
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

package com.maltaisn.notes.ui.sync.passwordchange

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogPasswordChangeBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.setCustomEndIconOnClickListener
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.common.ViewModelDialog
import com.maltaisn.notes.ui.sync.passwordchange.PasswordChangeViewModel.FieldError


class PasswordChangeDialog : ViewModelDialog() {

    private val viewModel: PasswordChangeViewModel by viewModels { viewModelFactory }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()

        val binding = DialogPasswordChangeBinding.inflate(
                LayoutInflater.from(context), null, false)

        val currLayout = binding.passwordCurrentEdtLayout
        val newLayout = binding.passwordNewEdtLayout
        val confirmLayout = binding.passwordConfirmEdtLayout

        newLayout.setCustomEndIconOnClickListener {
            val passwordHidden = binding.passwordNewEdt.transformationMethod is PasswordTransformationMethod
            viewModel.setPasswordConfirmEnabled(passwordHidden)
        }

        binding.passwordCurrentEdt.doAfterTextChanged {
            viewModel.setEnteredCurrentPassword(it?.toString() ?: "")
        }
        binding.passwordNewEdt.doAfterTextChanged {
            viewModel.setEnteredNewPassword(it?.toString() ?: "")
        }
        binding.passwordConfirmEdt.doAfterTextChanged {
            viewModel.setEnteredPasswordConfirm(it?.toString() ?: "")
        }

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
                .setView(binding.root)
                .setPositiveButton(R.string.action_password_change_short, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        binding.passwordCurrentEdt.requestFocus()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                binding.root.hideKeyboard()
                viewModel.changePassword()
            }

            // Observers
            viewModel.messageEvent.observe(this, EventObserver { messageId ->
                Snackbar.make(requireParentFragment().requireView(), messageId, Snackbar.LENGTH_SHORT).show()
            })

            viewModel.fieldError.observe(this, Observer { error ->
                val txvLayouts = listOf(currLayout, newLayout, confirmLayout)
                val errorLayout = when (error?.location) {
                    null -> null
                    FieldError.Location.PASSWORD_CURRENT -> binding.passwordCurrentEdtLayout
                    FieldError.Location.PASSWORD_NEW -> binding.passwordNewEdtLayout
                    FieldError.Location.PASSWORD_CONFIRM -> binding.passwordConfirmEdtLayout
                }
                for (layout in txvLayouts) {
                    if (layout === errorLayout) {
                        layout.error = getString(error!!.messageId, *error.args)
                    } else {
                        layout.error = null
                        layout.isErrorEnabled = false
                    }
                }
            })

            viewModel.passwordConfirmEnabled.observe(this, Observer { enabled ->
                confirmLayout.isVisible = enabled
                if (!enabled) {
                    binding.passwordConfirmEdt.text = null
                }
            })

            viewModel.dismissEvent.observe(this, EventObserver {
                dismiss()
            })
        }

        return dialog
    }

}
