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
import android.text.method.PasswordTransformationMethod
import android.view.View.OnClickListener
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogExportPasswordBinding
import com.maltaisn.notes.hideCursorInAllViews
import com.maltaisn.notes.setTitleIfEnoughSpace
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject

class ExportPasswordDialog : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ExportPasswordViewModel.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val binding = DialogExportPasswordBinding.inflate(layoutInflater, null, false)

        val passwordInput = binding.passwordInput
        val passwordLayout = binding.passwordInputLayout
        val passwordRepeatInput = binding.passwordRepeat
        val passwordRepeatLayout = binding.passwordRepeatLayout

        val builder = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val selectedPassword = passwordInput.text.toString()
                callback.onExportPasswordDialogPositiveButtonClicked(selectedPassword)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                callback.onExportPasswordDialogNegativeButtonClicked()
            }
            .setTitleIfEnoughSpace(R.string.encrypted_export_dialog_title)

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setCanceledOnTouchOutside(true)

        dialog.setOnShowListener {
            val okBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            viewModel.passwordValid.observe(this) { isPasswordValid ->
                okBtn.isEnabled = isPasswordValid
            }
            viewModel.passwordRepeatErrorShown.observe(this) { shouldShowError ->
                passwordRepeatLayout.error = if (shouldShowError) {
                    getString(R.string.encrypted_export_password_matching_error)
                } else {
                    ""
                }
            }
        }

        passwordInput.doAfterTextChanged {
            viewModel.onPasswordChanged(it?.toString() ?: "", passwordRepeatInput.text.toString())
        }
        passwordRepeatInput.doAfterTextChanged {
            viewModel.onPasswordChanged(passwordInput.text.toString(), it?.toString() ?: "")
        }
        passwordInput.requestFocus()

        val passwordToggleListener = OnClickListener {
            passwordLayout.togglePasswordVisibile()
            passwordRepeatLayout.togglePasswordVisibile()
        }
        passwordLayout.setEndIconOnClickListener(passwordToggleListener)
        passwordRepeatLayout.setEndIconOnClickListener(passwordToggleListener)
        passwordRepeatLayout.setErrorIconOnClickListener(passwordToggleListener)

        viewModel.setDialogDataEvent.observeEvent(this) { (password, passwordRepeat) ->
            passwordInput.setText(password)
            passwordRepeatInput.setText(passwordRepeat)
        }

        viewModel.start()

        return dialog
    }

    private fun TextInputLayout.togglePasswordVisibile() {
        val editText = editText ?: return
        // Store the current cursor position
        val selection = editText.selectionEnd

        // Check for existing password transformation
        val hasPasswordTransformation = editText.transformationMethod is PasswordTransformationMethod;
        if (hasPasswordTransformation) {
            editText.transformationMethod = null
        } else {
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        // Restore the cursor position
        editText.setSelection(selection)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideCursorInAllViews()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        callback.onExportPasswordDialogCancelled()
    }

    private val callback: Callback
        get() = (parentFragment as? Callback)
            ?: (activity as? Callback)
            ?: error("No callback for ExportPasswordDialog")

    interface Callback {
        fun onExportPasswordDialogPositiveButtonClicked(password: String) = Unit
        fun onExportPasswordDialogNegativeButtonClicked() = Unit
        fun onExportPasswordDialogCancelled() = Unit
    }

    companion object {
        fun newInstance(): ExportPasswordDialog {
            return ExportPasswordDialog()
        }
    }
}
