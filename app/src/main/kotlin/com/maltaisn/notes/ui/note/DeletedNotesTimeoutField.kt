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

package com.maltaisn.notes.ui.note

import com.maltaisn.notes.R
import com.maltaisn.notes.model.ValueEnum
import com.maltaisn.notes.model.findValueEnum

/**
 * Enum for deleted notes timeout.
 * [value] is from [R.array.pref_deleted_notes_timeout_values].
 */
enum class DeletedNotesTimeoutField(override val value: String) : ValueEnum<String> {
    DAY("1"),
    WEEK("7"),
    MONTH("30"),
    YEAR("365");

    companion object {
        fun fromValue(value: String): DeletedNotesTimeoutField = findValueEnum(value)
    }
}