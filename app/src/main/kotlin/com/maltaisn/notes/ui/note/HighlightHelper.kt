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

package com.maltaisn.notes.ui.note

import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.core.text.set
import com.maltaisn.notes.model.entity.ListNoteItem

/**
 * Helper class to add highlight spans on search results.
 */
object HighlightHelper {

    /**
     * Find a [max] of highlight positions for matches of a [query] in a [text].
     */
    fun findHighlightsInString(text: String, query: String, max: Int = Int.MAX_VALUE) = buildList<IntRange> {
        var i = 0
        while (i < text.length) {
            i = text.indexOf(query, i, ignoreCase = true)
            if (i == -1) {
                break
            }
            this += i..(i + query.length)
            if (size == max) {
                break
            }
            i++
        }
    }

    /**
     * Group highlights of a list note by item.
     * Highlight indices are also shifted to the item's content.
     */
    fun splitListNoteHighlightsByItem(items: List<ListNoteItem>, highlights: List<IntRange>): List<List<IntRange>> {
        var currHighlightIndex = 0
        var contentStartPos = 0
        val itemHighlights = mutableListOf<List<IntRange>>()
        for (item in items) {
            val contentEndPos = contentStartPos + item.content.length + 1
            itemHighlights += buildList<IntRange> {
                while (currHighlightIndex < highlights.size) {
                    val highlight = highlights[currHighlightIndex]
                    if (highlight.first >= contentStartPos && highlight.last < contentEndPos) {
                        this += (highlight.first - contentStartPos)..(highlight.last - contentStartPos)
                        currHighlightIndex++
                    } else {
                        break
                    }
                }
            }
            contentStartPos = contentEndPos
        }
        return itemHighlights
    }

    /**
     * Creates a spannable string of a [text] with background spans of a [bgColor] for [highlights].
     */
    fun getHighlightedText(text: String, highlights: List<IntRange>, bgColor: Int, fgColor: Int): CharSequence {
        if (highlights.isEmpty()) {
            return text
        }

        val highlightedText = SpannableString(text)
        for (highlight in highlights) {
            highlightedText[highlight] = BackgroundColorSpan(bgColor)
            highlightedText[highlight] = ForegroundColorSpan(fgColor)
        }
        return highlightedText
    }
}
