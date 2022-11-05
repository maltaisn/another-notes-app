/*
 * Copyright 2022 Nicolas Maltais
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

data class SortSettings(val field: SortField, val direction: SortDirection)

enum class SortField(override val value: String) : ValueEnum<String> {
    ADDED_DATE("added_date"),
    MODIFIED_DATE("modified_date"),
    TITLE("title");

    companion object {
        fun fromValue(value: String): SortField = findValueEnum(value)
    }
}

enum class SortDirection(override val value: String) : ValueEnum<String> {
    ASCENDING("ascending"),
    DESCENDING("descending");

    companion object {
        fun fromValue(value: String): SortDirection = findValueEnum(value)
    }
}
