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

package com.maltaisn.notes.ui.sync.accountdelete

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
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


class AccountDeleteDialog : ViewModelDialog() {

    private val viewModel: AccountDeleteViewModel by viewModels { viewModelFactory }


    @SuppressLint("InflateParams")
    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()

        val view = LayoutInflater.from(context).inflate(
                R.layout.dialog_account_delete, null, false)

        val passwordLayout: TextInputLayout = view.findViewById(R.id.edt_layout_password)
        val passwordEdt: EditText = view.findViewById(R.id.edt_password)

        passwordEdt.doAfterTextChanged {
            viewModel.onPasswordEntered(it?.toString() ?: "")
        }

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sync_account_delete)
                .setMessage(R.string.sync_account_delete_message)
                .setView(view)
                .setPositiveButton(R.string.action_delete, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        passwordEdt.requestFocus()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                view.hideKeyboard()
                viewModel.deleteAccount()
            }

            // Observers
            viewModel.deleteBtnEnabled.observe(this, Observer { enabled ->
                btn.isEnabled = enabled
            })

            viewModel.messageEvent.observe(this, EventObserver { messageId ->
                Snackbar.make(requireParentFragment().requireView(), messageId, Snackbar.LENGTH_SHORT).show()
            })

            viewModel.passwordError.observe(this, Observer { errorId ->
                if (errorId != null) {
                    passwordLayout.error = getString(errorId)
                } else {
                    passwordLayout.isErrorEnabled = false
                }
            })

            viewModel.dismissEvent.observe(this, EventObserver {
                dismiss()
            })
        }

        return dialog
    }

}
