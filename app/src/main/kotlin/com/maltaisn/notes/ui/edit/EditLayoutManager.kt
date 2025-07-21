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

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.adapter.EditEditText

/**
 * Custom [LinearLayoutManager] class used for the [EditFragment].
 */
class EditLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun onRequestChildFocus(
        parent: RecyclerView,
        state: RecyclerView.State,
        child: View,
        focused: View?
    ): Boolean {
        return if (child.id == R.id.content_edt) {
            // Override default behavior of scrolling to the start of children views on focus for the text note content
            // EditText. It can be very long and the user will scroll to where they want to edit, so we definitely don't
            // want to scroll back to the beginning right afterwards.
            // This works, except if the focus position ends up under the keyboard that shows up.
            // So bring the cursor into view after some delay, assuming the keyboard has been shown by then.
            child.postDelayed({
                child as EditEditText
                child.bringPointIntoView(child.selectionStart)
            }, 200)
            true
        } else {
            super.onRequestChildFocus(parent, state, child, focused)
        }
    }
}
