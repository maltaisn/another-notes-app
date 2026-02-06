/*
 * Copyright 2025 Nicolas Maltais
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

package com.maltaisn.notes.ui.edit

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.maltaisn.notes.R

class ColorPickerDialog : DialogFragment() {

    private val callback: Callback
        get() = (parentFragment as? Callback)
            ?: (activity as? Callback)
            ?: error("No callback for ColorPickerDialog")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.action_color)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        val selectedColor = arguments?.getInt(KEY_SELECTED_COLOR) ?: 0

        val buttons = listOf(
            view.findViewById<ImageView>(R.id.color_btn_0),
            view.findViewById<ImageView>(R.id.color_btn_1),
            view.findViewById<ImageView>(R.id.color_btn_2),
            view.findViewById<ImageView>(R.id.color_btn_3),
            view.findViewById<ImageView>(R.id.color_btn_4),
            view.findViewById<ImageView>(R.id.color_btn_5)
        )

        for ((index, button) in buttons.withIndex()) {
            if (index == selectedColor) {
                button.setImageResource(R.drawable.ic_check)
            }
            button.setOnClickListener {
                callback.onColorSelected(index)
                dismiss()
            }
        }

        return dialog
    }

    interface Callback {
        fun onColorSelected(color: Int)
    }

    companion object {
        private const val KEY_SELECTED_COLOR = "selected_color"
        const val TAG = "color_picker_dialog"

        fun newInstance(selectedColor: Int): ColorPickerDialog {
            val fragment = ColorPickerDialog()
            val args = Bundle()
            args.putInt(KEY_SELECTED_COLOR, selectedColor)
            fragment.arguments = args
            return fragment
        }
    }
}
