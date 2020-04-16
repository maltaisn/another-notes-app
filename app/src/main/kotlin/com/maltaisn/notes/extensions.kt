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

package com.maltaisn.notes

import android.content.Context
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputLayout


/**
 * Try to hide the keyboard from [this] view.
 */
fun View.hideKeyboard() {
    val context = this.context ?: return
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}

/**
 * Try to show the keyboard from [this] view.
 */
fun View.showKeyboard() {
    this.requestFocus()
    val context = this.context ?: return
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
}

/**
 * [TextInputLayout] doesn't really allow customizing the end icon click listener so this
 * is a copy of the click listener used internally, but this one also calls [listener].
 */
fun TextInputLayout.setCustomEndIconOnClickListener(listener: (View) -> Unit) =
        this.setEndIconOnClickListener { view ->
            // Store the current cursor position
            val edt = this.editText ?: return@setEndIconOnClickListener
            val oldSelection = edt.selectionEnd
            val hidePassword = edt.transformationMethod !is PasswordTransformationMethod
            edt.transformationMethod = PasswordTransformationMethod.getInstance().takeIf { hidePassword }
            if (oldSelection >= 0) {
                edt.setSelection(oldSelection)
            }
            listener(view)
        }
