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

package com.maltaisn.notes.ui.edit

import android.text.Editable
import android.text.TextWatcher
import com.maltaisn.notes.model.entity.Note


/**
 * A [TextWatcher] used for adding new bullet points to a text list when user inserts a line break.
 */
class BulletTextWatcher : TextWatcher {

    private var bulletChange: ((text: Editable) -> Unit)? = null


    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        if (text != null && count == 1 && text[start] == '\n') {
            // User inserted a single char, a line break.
            // Find start position of current line.
            var pos = start - 1
            while (pos >= 0) {
                if (text[pos] == '\n') {
                    break
                }
                pos--
            }
            val lineStart = pos + 1

            // Check if current line used a list bullet.
            val bullet = text[lineStart]
            if (bullet in Note.BULLET_CHARS) {
                // Get the bullet text, i.e the bullet with all following whitespaces.
                val bulletText = buildString {
                    this.append(bullet)
                    pos = lineStart + 1
                    while (pos < start) {
                        val c = text[pos]
                        if (!c.isWhitespace()) {
                            break
                        }
                        this.append(text[pos])
                        pos++
                    }
                }
                bulletChange = if (pos == start) {
                    // Last item was blank, create no new bullet and delete current one.
                    { it.delete(lineStart, start + 1) }
                } else {
                    // Last item wasn't blank, insert bullet text on new line.
                    { it.insert(start + 1, bulletText, 0, bulletText.length) }
                }
            }
        }
    }

    override fun afterTextChanged(text: Editable?) {
        // Apply bullet change, but set the change to null first, since applying it
        // will also result in call to afterTextChanged.
        val change = bulletChange
        bulletChange = null
        change?.invoke(text ?: return)
    }
}
