package com.maltaisn.notes.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.DialogImportPasswordBinding
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
        val binding = DialogImportPasswordBinding.inflate(LayoutInflater.from(context), null, false)

        val passwordInput = binding.passwordInput
        val showPasswordCheckbox = binding.showPasswordChk

        val builder = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val selectedPassword = passwordInput.text.toString()
                callback.onImportPasswordDialogPositiveButtonClicked(selectedPassword)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                callback.onImportPasswordDialogNegativeButtonClicked()
            }

        // Only show dialog title if screen size is under a certain dimension.
        // Otherwise it becomes much harder to type text, see #53.
        val dimen = context.resources.getDimension(R.dimen.label_edit_dialog_title_min_height) /
                context.resources.displayMetrics.density
        if (context.resources.configuration.screenHeightDp >= dimen) {
            builder.setTitle(R.string.encrypted_import_dialog_title)
        }

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setCanceledOnTouchOutside(true)

        showPasswordCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val transformationMethod = if (isChecked) null else PasswordTransformationMethod()
            passwordInput.transformationMethod = transformationMethod
        }

        // Cursor must be hidden when dialog is dismissed to prevent memory leak
        // See [https://stackoverflow.com/questions/36842805/dialogfragment-leaking-memory]
        dialog.setOnDismissListener {
            passwordInput.isCursorVisible = false
        }

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