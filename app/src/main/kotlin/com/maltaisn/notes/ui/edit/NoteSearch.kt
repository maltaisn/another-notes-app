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

/**
 * Search of a query in the text of a note, used to find and highlight search matches.
 */
object NoteSearch {

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Returns the terms to search for [query] in [texts].
     * Matches of the whole query are preferred. If there are none, since the search is not limited to exact
     * phrases, the words of the query are used instead. Returns an empty list if nothing matches.
     */
    fun findTerms(query: String, texts: List<CharSequence>): List<String> {
        val fullQuery = query.trim().trim('"')
        if (fullQuery.isEmpty()) {
            return emptyList()
        }
        val words = fullQuery.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        for (terms in listOf(listOf(fullQuery), words)) {
            if (texts.any { text -> terms.any { text.contains(it, ignoreCase = true) } }) {
                return terms
            }
        }
        return emptyList()
    }

    /**
     * Returns the case insensitive matches of [terms] in [text], in order of appearance.
     * Matches of the same term don't overlap, and only one match is kept per position.
     */
    fun findMatches(text: CharSequence, terms: List<String>): List<Match> {
        return terms.flatMap { term -> findAllOccurrences(text, term) }
            .sortedBy { it.start }
            .distinctBy { it.start }
    }

    private fun findAllOccurrences(text: CharSequence, term: String): List<Match> {
        val occurrences = mutableListOf<Match>()
        var i = text.indexOf(term, ignoreCase = true)
        while (i >= 0) {
            occurrences += Match(i, term.length)
            i = text.indexOf(term, i + term.length, ignoreCase = true)
        }
        return occurrences
    }

    data class Match(val start: Int, val length: Int) {
        val end: Int
            get() = start + length
    }
}
