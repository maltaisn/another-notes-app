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

/**
 * Defines a focus change event in [EditFragment] for a [EditTextItem].
 * The focus is changed to a item at [index], to a text position [textPos].
 * The focus change may have to be delayed if item doesn't exist yet ([itemExists]).
 */
data class EditFocusChange(
    val index: Int,
    val textPos: Int,
    val itemExists: Boolean
)