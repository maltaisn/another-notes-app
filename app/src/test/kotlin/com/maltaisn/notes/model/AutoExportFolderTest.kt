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

import org.junit.Test
import kotlin.test.assertEquals

class AutoExportFolderTest {

    @Test
    fun `should keep only most recent exports`() {
        val exports = (1..10).map { "2026-11-%02d_08-00-00.json".format(it) }
        assertEquals((1..3).map { "2026-11-%02d_08-00-00.json".format(it) },
            AutoExportFolder.exportsToDelete(exports.shuffled()).sorted())
    }

    @Test
    fun `should not delete anything when under limit`() {
        val exports = (1..AutoExportFolder.MAX_EXPORTS).map { "2026-11-%02d_08-00-00.json".format(it) }
        assertEquals(emptyList(), AutoExportFolder.exportsToDelete(exports))
    }

    @Test
    fun `should never delete files that are not exports`() {
        val others = listOf("notes.json", "(invalid).json", "2026-11-01.json", "photo.jpg",
            "2026-11-01_08-00-00.json.bak", "0000-00-00_00-00-00.txt")
        val exports = (1..10).map { "2026-11-%02d_08-00-00.json".format(it) }
        val deleted = AutoExportFolder.exportsToDelete(others + exports)
        assertEquals(3, deleted.size)
        assert(deleted.none { it in others })
    }

    @Test
    fun `should count duplicate-named exports from providers`() {
        val names = (1..7).map { "2026-11-%02d_08-00-00.json".format(it + 10) } +
            "2026-11-01_08-00-00 (1).json"
        assertEquals(listOf("2026-11-01_08-00-00 (1).json"), AutoExportFolder.exportsToDelete(names))
    }
}
