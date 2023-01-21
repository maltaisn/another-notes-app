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
import android.text.TextWatcher
import com.maltaisn.notes.model.entity.Note

/**
 * A [TextWatcher] used for adding new bullet points to a text list when user inserts a line break.
 */
class BulletTextWatcher : TextWatcher {

    private var bulletChange: ((text: Editable) -> Unit)? = null

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
//        Log.d(TAG, "beforeTextChanged(\"${text?.replace("\n".toRegex(), "\\\\n")}\", $start, $count, $after)")
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
//        Log.d(TAG, "onTextChanged(\"${text?.replace("\n".toRegex(), "\\\\n")}\", $start, $before, $count)")
        val end = start + count - 1
        if (text != null && count - before == 1 && text[end] == '\n') {
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
            val lineText = text.substring(lineStart, end)
            val bulletMatch = BULLET_REGEX.find(lineText)
            if (bulletMatch != null) {
                val bulletText = bulletMatch.value
                bulletChange = if (lineStart + bulletText.length == end) {
                    // Last item was blank, create no new bullet and delete current one (also delete newline).
                    { it.delete(lineStart, end + 1) }
                } else {
                    // Last item wasn't blank, insert bullet text on new line.
                    { it.insert(end + 1, bulletText, 0, bulletText.length) }
                }
            }
        }
    }

    override fun afterTextChanged(text: Editable?) {
        // Apply bullet change, but set the change to null first, since applying it
        // will also result in call to afterTextChanged.
//        Log.d(TAG, "afterTextChanged(\"${text?.replace("\n".toRegex(), "\\\\n")}\")")
        val change = bulletChange
        bulletChange = null
        change?.invoke(text ?: return)
    }

    companion object {
        val BULLET_REGEX = """^\s*[${Note.BULLET_CHARS}]\s*""".toRegex()
    }
}
