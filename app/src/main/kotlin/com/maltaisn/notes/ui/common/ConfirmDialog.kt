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

package com.maltaisn.notes.ui.common

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.R


class ConfirmDialog : DialogFragment() {

    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()
        val args = requireArguments()
        return MaterialAlertDialogBuilder(context)
                .setTitle(args.getInt(ARG_TITLE))
                .setMessage(args.getInt(ARG_MESSAGE))
                .setPositiveButton(args.getInt(ARG_BTN_POSITIVE)) { _, _ ->
                    (parentFragment as? Callback)?.onDialogConfirmed(tag)
                }
                .setNegativeButton(args.getInt(ARG_BTN_NEGATIVE), null)
                .create()
    }

    interface Callback {
        fun onDialogConfirmed(tag: String?)
    }

    companion object {

        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_BTN_POSITIVE = "btn_positive"
        private const val ARG_BTN_NEGATIVE = "btn_negative"

        fun newInstance(@StringRes title: Int,
                        @StringRes message: Int,
                        @StringRes btnPositive: Int,
                        @StringRes btnNegative: Int = R.string.action_cancel): ConfirmDialog {
            val dialog = ConfirmDialog()
            dialog.arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_BTN_POSITIVE to btnPositive,
                    ARG_BTN_NEGATIVE to btnNegative)
            return dialog
        }
    }

}
