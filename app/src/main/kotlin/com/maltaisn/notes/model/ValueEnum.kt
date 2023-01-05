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

package com.maltaisn.notes.model

/**
 * Enum with a value, so that it can be stored in future-proof manner
 * without relying on the field name or ordinal.
 */
interface ValueEnum<T> {
    val value: T
}

/**
 * Get value enum constant from its value.
 * Throws an exception if value doesn't match any enum constant.
 */
inline fun <reified V : ValueEnum<out T>, T> findValueEnum(value: T) =
    V::class.java.enumConstants?.find { it.value == value }
        ?: throw BadDataException("Unknown value")
