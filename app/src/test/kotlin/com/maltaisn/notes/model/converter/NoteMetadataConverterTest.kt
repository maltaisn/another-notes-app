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

package com.maltaisn.notes.model.converter

import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.ListNoteMetadata
import org.junit.Test
import kotlin.test.assertEquals

class NoteMetadataConverterTest {

    @Test
    fun `should convert blank metadata to string`() {
        assertEquals("""{"type":"blank"}""",
            NoteMetadataConverter.toString(BlankNoteMetadata))
    }

    @Test
    fun `should convert blank metadata json to metadata`() {
        assertEquals(BlankNoteMetadata,
            NoteMetadataConverter.toMetadata("""{"type":"blank"}"""))
    }

    @Test
    fun `should convert list metadata to string`() {
        assertEquals("""{"type":"list","checked":[false,true]}""",
            NoteMetadataConverter.toString(ListNoteMetadata(listOf(false, true))))
    }

    @Test
    fun `should convert list metadata json to metadata`() {
        assertEquals(ListNoteMetadata(listOf(false, true)),
            NoteMetadataConverter.toMetadata("""{"type":"list","checked":[false,true]}"""))
    }
}
