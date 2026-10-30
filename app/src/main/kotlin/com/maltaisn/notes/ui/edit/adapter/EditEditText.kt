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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.util.Linkify
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import androidx.core.text.getSpans
import androidx.core.text.util.LinkifyCompat
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.EditFragment
import com.maltaisn.notes.ui.edit.LinkArrowKeyMovementMethod
import com.maltaisn.notes.ui.edit.NoteSearch

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

    /**
     * Terms of the search query to highlight in the text, see [NoteSearch].
     * The highlight is drawn instead of being set as spans on the text, since there can be thousands of
     * matches for a short query and changing that many spans on each typed character is very slow.
     */
    var searchTerms: List<String> = emptyList()
        set(value) {
            if (value != field) {
                field = value
                searchMatches = null
                invalidate()
            }
        }

    /** Matches of [searchTerms] in the text, or `null` if they must be found again when drawing. */
    private var searchMatches: List<NoteSearch.Match>? = null

    private val searchHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_search_highlight)
    }
    private val currentSearchHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_search_highlight_current)
    }
    private val searchHighlightPath = Path()
    private val searchClipBounds = Rect()

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
                searchMatches = null
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

    override fun onDraw(canvas: Canvas) {
        drawSearchHighlights(canvas)
        super.onDraw(canvas)
    }

    /** Draw the search highlights behind the text, only for the visible lines. */
    private fun drawSearchHighlights(canvas: Canvas) {
        val layout = layout ?: return
        if (searchTerms.isEmpty() || !canvas.getClipBounds(searchClipBounds)) {
            return
        }
        val matches = searchMatches ?: NoteSearch.findMatches(text ?: "", searchTerms)
        searchMatches = matches
        if (matches.isEmpty()) {
            return
        }

        val dx = compoundPaddingLeft.toFloat()
        val dy = extendedPaddingTop.toFloat()
        val firstLine = layout.getLineForVertical((searchClipBounds.top - dy).toInt())
        val lastLine = layout.getLineForVertical((searchClipBounds.bottom - dy).toInt())
        val visibleStart = layout.getLineStart(firstLine)
        val visibleEnd = layout.getLineEnd(lastLine)

        // The current match is the one the cursor was moved to, it's highlighted differently.
        val cursorPos = if (hasFocus() && selectionStart == selectionEnd) selectionStart else -1

        // Matches are sorted by start, find the first one that may be visible.
        var i = matches.binarySearch { if (it.end <= visibleStart) -1 else 1 }.let { -(it + 1) }
        canvas.withTranslation(dx, dy) {
            while (i < matches.size && matches[i].start < visibleEnd) {
                val match = matches[i]
                layout.getSelectionPath(match.start, match.end, searchHighlightPath)
                val isCurrent = match.start == cursorPos
                drawPath(searchHighlightPath,
                    if (isCurrent) currentSearchHighlightPaint else searchHighlightPaint)
                i++
            }
        }
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
