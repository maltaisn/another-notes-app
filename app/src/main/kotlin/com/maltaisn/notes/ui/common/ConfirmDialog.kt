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

package com.maltaisn.notes.ui.common

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.R

/**
 * Simple dialog with a callback to ask user for confirmation.
 */
class ConfirmDialog : DialogFragment() {

    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()
        val args = requireArguments()
        val builder = MaterialAlertDialogBuilder(context)
            .setPositiveButton(args.getInt(ARG_BTN_POSITIVE)) { _, _ ->
                callback.onDialogPositiveButtonClicked(tag)
            }
            .setNegativeButton(args.getInt(ARG_BTN_NEGATIVE)) { _, _ ->
                callback.onDialogNegativeButtonClicked(tag)
            }

        // Set title if there's one
        val title = args.getInt(ARG_TITLE)
        if (title != 0) {
            builder.setTitle(title)
        }

        // Set message if there's one.
        val message = args.getInt(ARG_MESSAGE)
        if (message != 0) {
            builder.setMessage(message)
        } else {
            val messageStr = args.getString(ARG_MESSAGE_STR)
            if (!messageStr.isNullOrEmpty()) {
                builder.setMessage(messageStr);
            }
        }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        callback.onDialogCancelled(tag)
    }

    private val callback: Callback
        get() = (parentFragment as? Callback)
            ?: (activity as? Callback)
            ?: error("No callback for ConfirmDialog")

    interface Callback {
        fun onDialogPositiveButtonClicked(tag: String?) = Unit
        fun onDialogNegativeButtonClicked(tag: String?) = Unit
        fun onDialogCancelled(tag: String?) = Unit
    }

    companion object {

        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_BTN_POSITIVE = "btn_positive"
        private const val ARG_BTN_NEGATIVE = "btn_negative"
        private const val ARG_MESSAGE_STR = "message_str"

        fun newInstance(
            @StringRes title: Int = 0,
            @StringRes message: Int = 0,
            @StringRes btnPositive: Int,
            @StringRes btnNegative: Int = R.string.action_cancel,
            messageStr: String = "",
        ): ConfirmDialog {
            val dialog = ConfirmDialog()
            dialog.arguments = bundleOf(
                ARG_TITLE to title,
                ARG_MESSAGE to message,
                ARG_MESSAGE_STR to messageStr,
                ARG_BTN_POSITIVE to btnPositive,
                ARG_BTN_NEGATIVE to btnNegative)
            return dialog
        }
    }
}
