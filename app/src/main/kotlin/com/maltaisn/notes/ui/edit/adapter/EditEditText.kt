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

package com.maltaisn.notes.ui.edit.adapter

import android.content.Context
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.view.View
import android.view.textclassifier.TextLinks.TextLinkSpan
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.use
import androidx.core.text.getSpans
import androidx.core.text.util.LinkifyCompat
import androidx.core.widget.doAfterTextChanged
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

    var onLinkClickListener: ((text: String, url: String) -> Unit)? = null

    init {
        autoLink = context.obtainStyledAttributes(attrs, R.styleable.EditEditText, defStyleAddr, 0).use {
            it.getBoolean(R.styleable.EditEditText_autoLink, false)
        }

        doAfterTextChanged { editable ->
            if (editable == null) return@doAfterTextChanged
            // Might not remove all spans but will work for most of them.
            val spansToRemove = editable.getSpans<CharacterStyle>()
            for (span in spansToRemove) {
                editable.removeSpan(span)
            }
        }

        addOnAttachStateChangeListener(PrepareCursorControllersListener())

        if (autoLink) {
            doAfterTextChanged { editable ->
                // Add new links
                if (editable == null) return@doAfterTextChanged
                LinkifyCompat.addLinks(editable,
                    Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)
                LinkifyCompat.addLinks(editable, URL_REGEX, null)
            }

            movementMethod = LinkArrowKeyMovementMethod.getInstance()
        }
    }

    fun onLinkClicked(text: String, url: String) {
        onLinkClickListener?.invoke(text, url)
    }

    companion object {
        private val URL_REGEX = """[a-z]+://[^ \n]+""".toRegex().toPattern()
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
