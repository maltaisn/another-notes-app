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

import android.app.Dialog
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager

/**
 * Returns whether this fragment manager contains a fragment with a [tag].
 */
operator fun FragmentManager.contains(tag: String) = this.findFragmentByTag(tag) != null

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
