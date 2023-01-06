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

import javax.crypto.SecretKey

interface JsonManager {

    /**
     * Export all the app data to a JSON string.
     */
    suspend fun exportJsonData(): String

    /**
     * Import notes data from JSON, merging with existing data.
     * Returns true if import was successful, false otherwise.
     */
    suspend fun importJsonData(data: String, importKey: SecretKey? = null): ImportResult

    enum class ImportResult {
        SUCCESS,
        BAD_FORMAT,
        BAD_DATA,
        FUTURE_VERSION,
        KEY_MISSING_OR_INCORRECT,
    }
}
