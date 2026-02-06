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

package com.maltaisn.notes

import android.graphics.Paint
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.getSystemService

/**
 * Try to hide the keyboard from [this] view.
 */
fun View.hideKeyboard() {
    val context = this.context ?: return
    val imm = context.getSystemService<InputMethodManager>()
    imm?.hideSoftInputFromWindow(this.windowToken, 0)
}

@ColorRes
fun getNoteColorResource(index: Int): Int = when (index) {
    0 -> R.color.note_color_default
    1 -> R.color.note_color_1
    2 -> R.color.note_color_2
    3 -> R.color.note_color_3
    4 -> R.color.note_color_4
    5 -> R.color.note_color_5
    else -> R.color.note_color_default
}

/**
 * Try to show the keyboard from [this] view.
 */
fun View.showKeyboard(delay: Long = 200L) {
    val context = this.context ?: return
    this.postDelayed({
        val focus = this.findFocus() ?: return@postDelayed
        val imm = context.getSystemService<InputMethodManager>()
        imm?.showSoftInput(focus, 0)
    }, delay)
}

/**
 * Whether to draw strikethrough on text or not.
 */
var TextView.strikethroughText: Boolean
    get() = (this.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG) != 0
    set(value) {
        this.paintFlags = if (value) {
            this.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            this.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }
