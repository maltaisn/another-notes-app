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

package com.maltaisn.notes.ui.search

import org.junit.Test
import kotlin.test.assertEquals

class SearchQueryCleanerTest {

    @Test
    fun `should add wildcard to all terms`() {
        assertEquals("a* b* c*",
            SearchQueryCleaner.clean("a  b  c"))
    }

    @Test
    fun `shouldn't add wildcard in quotes`() {
        assertEquals("""a* "b c"""",
            SearchQueryCleaner.clean("""a "b c""""))
    }

    @Test
    fun `should remove disabled chars`() {
        assertEquals("a* columnb* c*",
            SearchQueryCleaner.clean("^a column:b c* ( )\\"))
    }

    @Test
    fun `should remove disabled chars in quotes`() {
        assertEquals("""a* "columnb c"""",
            SearchQueryCleaner.clean("""^a "column:b c*""""))
    }

    @Test
    fun `should disable fts keywords`() {
        assertEquals("a* and* b* or* not* c*",
            SearchQueryCleaner.clean("a AND (b OR NOT c)"))
    }

    @Test
    fun `should add missing quote`() {
        assertEquals("""a* "b c"""",
            SearchQueryCleaner.clean("""a "b c"""))
    }

    @Test
    fun `should allow minus NOT operator`() {
        assertEquals("a* -b*",
            SearchQueryCleaner.clean("a -b"))
    }

    @Test
    fun `should ignore extra separators`() {
        assertEquals("a* b* c*",
            SearchQueryCleaner.clean(";, a,;;  ,b   ;;c ;;,  "))
    }

    @Test
    fun `should ignore negative if only term`() {
        assertEquals("a*",
            SearchQueryCleaner.clean("-a"))
        assertEquals("a*",
            SearchQueryCleaner.clean("-  a"))
        assertEquals("a*",
            SearchQueryCleaner.clean(" -a"))
    }

    @Test
    fun `should ignore query of only separators`() {
        assertEquals("",
            SearchQueryCleaner.clean("        "))
    }

    @Test
    fun `should ignore empty query`() {
        assertEquals("", SearchQueryCleaner.clean(""))
    }
}
