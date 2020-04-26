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

package com.maltaisn.notes

import com.maltaisn.notes.model.entity.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.*


fun testNote(
        id: Long = Note.NO_ID,
        uuid: String = Note.generateNoteUuid(),
        type: NoteType = NoteType.TEXT,
        title: String = "note",
        content: String = "content",
        metadata: NoteMetadata = BlankNoteMetadata,
        added: Date = Date(),
        modified: Date = Date(),
        status: NoteStatus = NoteStatus.ACTIVE,
        synced: Boolean = true
) = Note(id, uuid, type, title, content, metadata, added, modified, status, synced)

fun listNote(
        items: List<ListNoteItem>,
        id: Long = Note.NO_ID,
        uuid: String = id.toString(),
        title: String = "note",
        added: Date = Date(),
        modified: Date = Date(),
        status: NoteStatus = NoteStatus.ACTIVE,
        synced: Boolean = true
) = Note(id, uuid, NoteType.LIST, title, items.joinToString("\n") { it.content },
        ListNoteMetadata(items.map { it.checked }), added, modified, status, synced)


fun assertNoteEquals(expected: Note, actual: Note,
                     dateEpsilon: Long = 1000,
                     ignoreId: Boolean = true,
                     ignoreUuid: Boolean = true) {
    assertTrue("Notes have different added dates.",
            (expected.addedDate.time - actual.addedDate.time) < dateEpsilon)
    assertTrue("Notes have different last modified dates.",
            (expected.lastModifiedDate.time - actual.lastModifiedDate.time) < dateEpsilon)
    assertEquals(expected, actual.copy(
            id = if (ignoreId) expected.id else actual.id,
            uuid = if (ignoreUuid) expected.uuid else actual.uuid,
            addedDate = expected.addedDate,
            lastModifiedDate = expected.lastModifiedDate))
}
