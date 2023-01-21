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

package com.maltaisn.notes.ui.edit

import android.text.Editable
import android.text.InputFilter
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class BulletTextWatcherTest {

    private lateinit var textWatcher: BulletTextWatcher

    @Before
    fun before() {
        textWatcher = BulletTextWatcher()
    }

    @Test
    fun `should add bullet simple`() {
        textWatcher.beforeTextChanged("- hey\n", 2, 3, 4)
        textWatcher.onTextChanged("- hey\n\n", 2, 3, 4)
        val text = E("- hey\n\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("- hey\n- \n"), text)
    }

    @Test
    fun `should add bullet simple single char change`() {
        textWatcher.beforeTextChanged("- hey", 5, 0, 1)
        textWatcher.onTextChanged("- hey\n", 5, 0, 1)
        val text = E("- hey\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("- hey\n- "), text)
    }

    @Test
    fun `should add bullet star`() {
        textWatcher.beforeTextChanged("*test", 1, 4, 5)
        textWatcher.onTextChanged("*test\n", 1, 4, 5)
        val text = E("*test\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("*test\n*"), text)
    }

    @Test
    fun `should add bullet indented`() {
        textWatcher.beforeTextChanged("- level 1\n    - level 2", 23, 0, 1)
        textWatcher.onTextChanged("- level 1\n    - level 2\n", 23, 0, 1)
        val text = E("- level 1\n    - level 2\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("- level 1\n    - level 2\n    - "), text)
    }

    @Test
    fun `should remove bullet`() {
        textWatcher.beforeTextChanged("- level 1\n- ", 12, 0, 1)
        textWatcher.onTextChanged("- level 1\n- \n", 12, 0, 1)
        val text = E("- level 1\n- \n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("- level 1\n"), text)
    }

    @Test
    fun `should remove bullet indented`() {
        textWatcher.beforeTextChanged("- level 1\n    + level 2\n    + ", 30, 0, 1)
        textWatcher.onTextChanged("- level 1\n    + level 2\n    + \n", 30, 0, 1)
        val text = E("- level 1\n    + level 2\n    + \n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("- level 1\n    + level 2\n"), text)
    }

    @Test
    fun `should add bullet first level indented`() {
        textWatcher.beforeTextChanged("     - indented", 7, 8, 9)
        textWatcher.onTextChanged("     - indented\n", 7, 8, 9)
        val text = E("     - indented\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("     - indented\n     - "), text)
    }

    @Test
    fun `should add bullet with many spaces between bullet`() {
        textWatcher.beforeTextChanged("     -      a", 12, 1, 2)
        textWatcher.onTextChanged("     -      a\n", 12, 1, 2)
        val text = E("     -      a\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("     -      a\n     -      "), text)
    }

    @Test
    fun `should add bullet when splitting word`() {
        textWatcher.beforeTextChanged("- splitword", 7, 0, 1)
        textWatcher.onTextChanged("- split\nword", 7, 0, 1)
        val text = E("- split\nword")
        textWatcher.afterTextChanged(text)
        assertEquals(E("- split\n- word"), text)
    }

    @Test
    fun `should not add bullet (no bullet)`() {
        textWatcher.beforeTextChanged("hey", 0, 3, 4)
        textWatcher.onTextChanged("hey\n", 0, 3, 4)
        val text = E("hey\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("hey\n"), text)
    }

    @Test
    fun `should not add bullet (invalid char)`() {
        textWatcher.beforeTextChanged("& hey", 2, 3, 4)
        textWatcher.onTextChanged("& hey\n", 2, 3, 4)
        val text = E("& hey\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("& hey\n"), text)
    }

    @Test
    fun `should not add bullet (non whitespace before bullet char)`() {
        textWatcher.beforeTextChanged("foo-bar", 0, 7, 8)
        textWatcher.onTextChanged("foo-bar\n", 0, 7, 8)
        val text = E("foo-bar\n")
        textWatcher.afterTextChanged(text)
        assertEquals(E("foo-bar\n"), text)
    }

    /** Thin wrapper around StringBuilder to make it an Editable. */
    class E(s: CharSequence) : Editable {
        private val sb = StringBuilder(s)

        override fun get(index: Int): Char {
            throw RuntimeException()
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            throw RuntimeException()
        }

        override fun getChars(start: Int, end: Int, dest: CharArray?, destoff: Int) {
            throw RuntimeException()
        }

        override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> {
            throw RuntimeException()
        }

        override fun getSpanStart(tag: Any?): Int {
            throw RuntimeException()
        }

        override fun getSpanEnd(tag: Any?): Int {
            throw RuntimeException()
        }

        override fun getSpanFlags(tag: Any?): Int {
            throw RuntimeException()
        }

        override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int {
            throw RuntimeException()
        }

        override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
            throw RuntimeException()
        }

        override fun removeSpan(what: Any?) {
            throw RuntimeException()
        }

        override fun clearSpans() {
            throw RuntimeException()
        }

        override fun setFilters(filters: Array<out InputFilter>?) {
            throw RuntimeException()
        }

        override fun getFilters(): Array<InputFilter> {
            throw RuntimeException()
        }

        override val length: Int
            get() = sb.length

        override fun append(text: CharSequence?) = apply { sb.append(text) }

        override fun append(text: CharSequence?, start: Int, end: Int) =
            apply { sb.append(text, start, end) }

        override fun append(text: Char) = apply { sb.append(text) }

        override fun replace(st: Int, en: Int, source: CharSequence, start: Int, end: Int) =
            apply { sb.replace(st, en, source.subSequence(start, end).toString()) }

        override fun replace(st: Int, en: Int, text: CharSequence) =
            apply { sb.replace(st, en, text.toString()) }

        override fun insert(where: Int, text: CharSequence, start: Int, end: Int) =
            apply { sb.insert(where, text.subSequence(start, end)) }

        override fun insert(where: Int, text: CharSequence) = apply { sb.insert(where, text) }

        override fun delete(st: Int, en: Int) = apply { sb.delete(st, en) }

        override fun clear() {
            sb.clear()
        }

        override fun equals(other: Any?) = other === this || other is E && other.sb.toString() == sb.toString()

        override fun hashCode() = sb.hashCode()

        override fun toString() = sb.toString()
    }
}
