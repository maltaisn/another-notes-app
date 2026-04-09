/*
 * Copyright 2026 Nicolas Maltais
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

package com.maltaisn.notes.model

import com.maltaisn.notes.R

/**
 * Enum for automatic export output formats.
 * [value] is from [R.array.pref_auto_export_format_values].
 */
enum class AutoExportFormat(override val value: String) : ValueEnum<String> {
    JSON("json"),
    ZIP("zip");

    companion object {
        fun fromValue(value: String): AutoExportFormat = findValueEnum(value)
    }
}
