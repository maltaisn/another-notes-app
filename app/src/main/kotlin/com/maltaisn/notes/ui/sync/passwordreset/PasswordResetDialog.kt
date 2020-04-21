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

package com.maltaisn.notes.ui.sync.passwordreset

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.common.ViewModelDialog


class PasswordResetDialog : ViewModelDialog() {

    private val viewModel: PasswordResetViewModel by viewModels { viewModelFactory }


    @SuppressLint("InflateParams")
    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()

        val view = LayoutInflater.from(context).inflate(
                R.layout.dialog_password_reset, null, false)

        val emailLayout: TextInputLayout = view.findViewById(R.id.edt_layout_email)
        val emailEdt: EditText = view.findViewById(R.id.edt_email)

        emailEdt.doAfterTextChanged {
            viewModel.onEmailEntered(it?.toString() ?: "")
        }

        // Set initial email
        if (state == null) {
            val args = requireArguments()
            if (args.containsKey(ARG_EMAIL)) {
                emailEdt.setText(args.getString(ARG_EMAIL))
            }
        }

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
                .setView(view)
                .setPositiveButton(R.string.action_password_reset_short, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        emailEdt.requestFocus()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                view.hideKeyboard()
                viewModel.resetPassword()
            }

            // Observers
            viewModel.resetBtnEnabled.observe(this, Observer { enabled ->
                btn.isEnabled = enabled
            })

            viewModel.messageEvent.observe(this, EventObserver { messageId ->
                Snackbar.make(requireParentFragment().requireView(), messageId, Snackbar.LENGTH_SHORT).show()
            })

            viewModel.emailError.observe(this, Observer { errorId ->
                if (errorId != null) {
                    emailLayout.error = getString(errorId)
                } else {
                    emailLayout.isErrorEnabled = false
                }
            })

            viewModel.dismissEvent.observe(this, EventObserver {
                dismiss()
            })
        }

        return dialog
    }

    companion object {

        private const val ARG_EMAIL = "email"

        fun newInstance(initialEmail: String): PasswordResetDialog {
            val dialog = PasswordResetDialog()
            dialog.arguments = bundleOf(ARG_EMAIL to initialEmail)
            return dialog
        }
    }

}
