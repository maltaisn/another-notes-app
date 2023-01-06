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

package com.maltaisn.notes.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogImportPasswordBinding
import com.maltaisn.notes.hideCursorInAllViews
import com.maltaisn.notes.setTitleIfEnoughSpace
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject

class ImportPasswordDialog : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ImportPasswordViewModel.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val binding = DialogImportPasswordBinding.inflate(layoutInflater, null, false)

        val passwordInput = binding.passwordInput

        val builder = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val selectedPassword = passwordInput.text.toString()
                callback.onImportPasswordDialogPositiveButtonClicked(selectedPassword)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                callback.onImportPasswordDialogNegativeButtonClicked()
            }
            .setTitleIfEnoughSpace(R.string.encrypted_import_dialog_title)

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setCanceledOnTouchOutside(true)

        passwordInput.doAfterTextChanged {
            val okBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            okBtn.isEnabled = it?.isNotEmpty() ?: false
            viewModel.onPasswordChanged(it?.toString() ?: "")
        }

        passwordInput.requestFocus()
        viewModel.setDialogDataEvent.observeEvent(this) { password ->
            passwordInput.setText(password)
        }

        viewModel.start()

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideCursorInAllViews()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        callback.onImportPasswordDialogCancelled()
    }

    private val callback: Callback
        get() = (parentFragment as? Callback)
            ?: (activity as? Callback)
            ?: error("No callback for ImportPasswordDialog")

    interface Callback {
        fun onImportPasswordDialogPositiveButtonClicked(password: String) = Unit
        fun onImportPasswordDialogNegativeButtonClicked() = Unit
        fun onImportPasswordDialogCancelled() = Unit
    }

    companion object {
        fun newInstance(): ImportPasswordDialog {
            return ImportPasswordDialog()
        }
    }
}