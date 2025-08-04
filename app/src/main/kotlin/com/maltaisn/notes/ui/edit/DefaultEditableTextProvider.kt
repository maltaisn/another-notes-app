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

import android.text.Editable
import javax.inject.Inject

class DefaultEditableText(override val text: Editable) : EditableText {

    override fun append(text: CharSequence) {
        this.text.append(text)
    }

    override fun replace(start: Int, end: Int, text: CharSequence) {
        this.text.replace(start, end, text)
    }
}

class DefaultEditableTextProvider @Inject constructor() : EditableTextProvider {
    override fun create(text: CharSequence): EditableText {
        // Use the same editable that EditText would normally construct.
        return DefaultEditableText(Editable.Factory.getInstance().newEditable(text))
    }
}