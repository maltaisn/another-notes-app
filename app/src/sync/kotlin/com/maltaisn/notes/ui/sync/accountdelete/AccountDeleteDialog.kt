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
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogAccountDeleteBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider


class AccountDeleteDialog : DialogFragment() {

    @Inject lateinit var viewModelProvider: Provider<AccountDeleteViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()

        val binding = DialogAccountDeleteBinding.inflate(
                LayoutInflater.from(context), null, false)

        val passwordEdt = binding.passwordEdt
        passwordEdt.doAfterTextChanged {
            viewModel.onPasswordEntered(it?.toString() ?: "")
        }

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
                .setView(binding.root)
                .setPositiveButton(R.string.action_delete, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        passwordEdt.requestFocus()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                passwordEdt.hideKeyboard()
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
                val layout = binding.passwordEdtLayout
                if (errorId != null) {
                    layout.error = getString(errorId)
                } else {
                    layout.isErrorEnabled = false
                }
            })

            viewModel.dismissEvent.observe(this, EventObserver {
                dismiss()
            })
        }

        return dialog
    }

}
