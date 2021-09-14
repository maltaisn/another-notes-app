/*
 * Copyright 2021 Nicolas Maltais
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

/**
 * Helper class to add highlight spans on search results.
 */
object HighlightHelper {

    const val START_ELLIPSIS = "\u2026\uFEFF"

    /**
     * Find a [max] of highlight positions for matches of a [query] in a [text].
     *
     * Note: could be improved to tokenize query in the same way FTS does, to separate terms
     * and highlight them separatedly.
     */
    fun findHighlightsInString(text: String, query: String, max: Int = Int.MAX_VALUE): MutableList<IntRange> {
        val highlights = mutableListOf<IntRange>()
        var queryClean = query
        if (query.first() == '"' && query.last() == '"') {
            queryClean = queryClean.substring(1, queryClean.length - 1)
        }
        if (max > 0) {
            var i = 0
            while (i < text.length) {
                i = text.indexOf(queryClean, i, ignoreCase = true)
                if (i == -1) {
                    break
                }
                highlights += i..(i + queryClean.length)
                if (highlights.size == max) {
                    break
                }
                i++
            }
        }
        return highlights
    }

    /**
     * Ellipsize start of text of first highlight is beyond threshold.
     * Leave a certain number of characters between the ellipsis and the highlight (the "distance")
     * If start is ellipsized, highlights are shifted according, in place.
     */
    fun getStartEllipsizedText(
        text: String,
        highlights: MutableList<IntRange>,
        startEllipsisThreshold: Int,
        startEllipsisDistance: Int
    ): Highlighted {
        var ellipsizedText = text
        if (highlights.isNotEmpty()) {
            val firstIndex = highlights.first().first
            if (firstIndex > startEllipsisThreshold) {
                var highlightShift = firstIndex - minOf(startEllipsisDistance, startEllipsisThreshold)
                // Skip white space between ellipsis start and text
                while (text[highlightShift].isWhitespace()) {
                    highlightShift++
                }
                highlightShift -= START_ELLIPSIS.length
                if (highlightShift > 0) {
                    ellipsizedText = START_ELLIPSIS + text.substring(highlightShift + START_ELLIPSIS.length)
                    for ((i, highlight) in highlights.withIndex()) {
                        highlights[i] = (highlight.first - highlightShift)..(highlight.last - highlightShift)
                    }
                }
            }
        }
        return Highlighted(ellipsizedText, highlights)
    }
}
