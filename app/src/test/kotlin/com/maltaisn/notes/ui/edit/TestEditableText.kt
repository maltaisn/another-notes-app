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

import com.maltaisn.notes.ui.edit.adapter.EditTextItem
import com.maltaisn.notes.ui.getOrAwaitValue

class TestEditableText(
    text: CharSequence = "",
    private val viewModel: EditViewModel? = null
) : EditableText {
    override val text = StringBuilder(text)

    override fun replace(start: Int, end: Int, text: CharSequence) {
        val oldText = this.text.substring(start, end)
        this.text.replace(start, end, text.toString())

        if (viewModel != null) {
            // Notify the view model that the text changed, if the item exists.
            val pos = viewModel.editItems.getOrAwaitValue().indexOfFirst { (it as? EditTextItem)?.text === this }
            if (pos != -1) {
                viewModel.onTextChanged(pos, start, end, oldText, text.toString())
            }
        }
    }

    override fun append(text: CharSequence) {
        replace(this.text.length, this.text.length, text)
    }

    override fun equals(other: Any?) = (other is TestEditableText && other.text.toString() == text.toString())

    override fun hashCode() = text.hashCode()

    override fun toString() = text.toString()
}

class TestEditableTextProvider : EditableTextProvider {
    override fun create(text: CharSequence): EditableText {
        return TestEditableText(text)
    }
}

val String.e: EditableText
    get() = TestEditableText(this)
