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

package com.maltaisn.notes

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Set [this] dialog maximum width to [maxWidth].
 * @param view The dialog's content view.
 */
fun Dialog.setMaxWidth(maxWidth: Int, view: View) {
    // Get current dialog's width and padding
    val fgPadding = Rect()
    val window = this.window!!
    window.decorView.background.getPadding(fgPadding)
    val padding = fgPadding.left + fgPadding.right
    var width = this.context.resources.displayMetrics.widthPixels - padding

    // Set dialog's dimensions, with maximum width.
    if (width > maxWidth) {
        width = maxWidth
    }
    window.setLayout(width + padding, FrameLayout.LayoutParams.WRAP_CONTENT)
    view.layoutParams = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
}

/**
 * Set title on dialog, but only if the device vertical size is large enough.
 * Otherwise, it becomes much harder or even impossible to type text (see #53).
 */
fun MaterialAlertDialogBuilder.setTitleIfEnoughSpace(@StringRes title: Int): MaterialAlertDialogBuilder {
    val dimen = context.resources.getDimension(R.dimen.input_dialog_title_min_height) /
            context.resources.displayMetrics.density
    if (context.resources.configuration.screenHeightDp >= dimen) {
        setTitle(title)
    }
    return this
}

/**
 * In dialogs with an EditText, the cursor must be hidden when dialog is dismissed to prevent memory leak.
 * See [https://stackoverflow.com/questions/36842805/dialogfragment-leaking-memory].
 * This should be called in [DialogFragment.onDismiss].
 */
@SuppressLint("WrongConstant")
fun DialogFragment.hideCursorInAllViews() {
    val view = dialog?.window?.decorView ?: return
    for (focusableView in view.getFocusables(View.FOCUSABLES_ALL)) {
        if (focusableView is TextView) {
            focusableView.isCursorVisible = false
        }
    }
}
