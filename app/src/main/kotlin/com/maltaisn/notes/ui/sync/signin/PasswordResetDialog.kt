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

package com.maltaisn.notes.ui.sync.signin

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard


class PasswordResetDialog : DialogFragment() {

    @SuppressLint("InflateParams")
    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()

        val view = LayoutInflater.from(context).inflate(
                R.layout.dialog_password_reset, null, false)
        val emailEdt: EditText = view.findViewById(R.id.edt_password_reset)

        // Set initial email
        if (state == null) {
            val args = requireArguments()
            if (args.containsKey(ARG_EMAIL)) {
                emailEdt.setText(args.getString(ARG_EMAIL))
            }
        }

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sync_password_reset)
                .setMessage(R.string.sync_password_reset_message)
                .setView(view)
                .setPositiveButton(R.string.action_reset) { _, _ ->
                    view.hideKeyboard()
                    (parentFragment as? Callback)?.onPasswordResetButtonClicked(emailEdt.text.toString())
                }
                .setNegativeButton(R.string.action_cancel, null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        dialog.setOnShowListener {
            // Add listener to disable "Reset" button when email is blank.
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.isEnabled = false
            emailEdt.doAfterTextChanged {
                btn.isEnabled = it?.isNotBlank() ?: false
            }
            emailEdt.requestFocus()
        }

        return dialog
    }

    interface Callback {
        fun onPasswordResetButtonClicked(email: String)
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
