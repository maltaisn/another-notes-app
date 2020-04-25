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

package com.maltaisn.notes.ui.search


/**
 * Helper class to clean search queries.
 */
object SearchQueryCleaner {

    /**
     * Clean a search [query] to disable all unwanted FTS features, in order to
     * prevent crashes on malformed queries, like when quotes are missing.
     * Also adds a prefix to each search term to widen the search.
     *
     * Only balanced quotes and NOT operator using a minus `-` are allowed.
     */
    fun clean(query: String) = buildString {
        var inQuotes = false
        var inSearchTerm = false
        loop@ for (c in query) {
            when (c) {
                '"' -> {
                    inQuotes = !inQuotes
                    inSearchTerm = false
                    append('"')
                }
                '^', ':', '*', '(', ')', '\\' -> {
                    // Disable column start, column name separator, wildcard,
                    // boolean operators grouping parenthesis, escapes.
                    continue@loop
                }
                ' ', ',', ';' -> {
                    // Last search term has ended, add wildcard and separator
                    if (inSearchTerm) {
                        if (!inQuotes) {
                            append('*')
                        }
                        append(' ')
                        inSearchTerm = false
                    }
                }
                else -> {
                    // Lowercase char to disable MATCH keywords.
                    append(c.toLowerCase())
                    inSearchTerm = true
                }
            }
        }
        if (inQuotes) {
            // Add missing quote
            append('"')
            inSearchTerm = false
        }
        if (inSearchTerm) {
            append("*")
        }
        if (length > 0 && this[lastIndex] == ' ') {
            deleteCharAt(lastIndex)
        }
    }

}
