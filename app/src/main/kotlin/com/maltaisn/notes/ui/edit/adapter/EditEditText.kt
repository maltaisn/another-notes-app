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

package com.maltaisn.notes.ui.edit.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.util.Linkify
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.text.getSpans
import androidx.core.text.util.LinkifyCompat
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.EditFragment
import com.maltaisn.notes.ui.edit.LinkArrowKeyMovementMethod

/**
 * Custom [EditText] class used for all fields of the [EditFragment].
 */
class EditEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAddr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAddr) {

    val autoLink: Boolean
    val textSizeMultiplier: Float

    var onLinkClickListener: ((text: String, url: String) -> Unit)? = null
    var onTextChangedForUndoListener: ((start: Int, end: Int, oldText: String, newText: String) -> Unit)? = null

    private var ignoreTextChanges: Boolean = false

    init {
        @SuppressLint("UseKtx")
        val attrs = context.obtainStyledAttributes(attrs, R.styleable.EditEditText, defStyleAddr, 0)
        autoLink = attrs.getBoolean(R.styleable.EditEditText_autoLink, false)
        textSizeMultiplier = attrs.getFloat(R.styleable.EditEditText_textSizeMultiplier, 1.0f)
        attrs.recycle()

        addTextChangedListener(object : TextWatcher {
            private var oldText: String = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                oldText = s?.substring(start, start + count).orEmpty()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!ignoreTextChanges) {
                    onTextChangedForUndoListener?.invoke(
                        start, start + before, oldText, s?.substring(start, start + count) ?: "")
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null) return

                // Might not remove all spans but will work for most of them.
                val spansToRemove = s.getSpans<CharacterStyle>()
                for (span in spansToRemove) {
                    s.removeSpan(span)
                }

                if (autoLink) {
                    // Add new links
                    // Maybe this should be debounced for performance?
                    LinkifyCompat.addLinks(s, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)
                }
            }
        })

        addOnAttachStateChangeListener(PrepareCursorControllersListener())

        if (autoLink) {
            movementMethod = LinkArrowKeyMovementMethod.getInstance()
        }
    }

    fun onLinkClicked(text: String, url: String) {
        onLinkClickListener?.invoke(text, url)
    }

    fun setTextIgnoringUndo(text: CharSequence) {
        ignoreTextChanges = true
        this.setText(text)
        ignoreTextChanges = false
    }

    fun setAutoTextSize(size: Float) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size * textSizeMultiplier)
    }
}

/**
 * Used to fix the issue described at [https://stackoverflow.com/q/54833004],
 * causing the EditText long press to fail after a view holder has been recycled.
 */
private class PrepareCursorControllersListener : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(view: View) {
        if (view !is EditText) {
            return
        }
        view.isCursorVisible = false
        view.isCursorVisible = true
    }

    override fun onViewDetachedFromWindow(v: View) = Unit
}
