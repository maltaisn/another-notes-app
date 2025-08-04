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
 * This is needed so that the view model can know the text and each item at all times and be able
 * to change it. An interface is used so we can provide a different implementation for testing,
 * via the [EditableTextProvider].
 */
interface EditableText {
    val text: CharSequence

    fun append(text: CharSequence)
    fun replace(start: Int, end: Int, text: CharSequence)

    fun replaceAll(text: CharSequence) {
        replace(0, this.text.length, text)
    }
}

interface EditableTextProvider {
    /**
     * Create an editable with an initial [text].
     */
    fun create(text: CharSequence): EditableText
}
