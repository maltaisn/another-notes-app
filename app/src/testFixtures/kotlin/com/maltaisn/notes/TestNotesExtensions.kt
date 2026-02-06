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

@file:Suppress("LongParameterList")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.maltaisn.notes

import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.FractionalIndex
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun testNote(
    id: Long = Note.NO_ID,
    type: NoteType = NoteType.TEXT,
    title: String = "note",
    content: String = "content",
    metadata: NoteMetadata = BlankNoteMetadata,
    added: Date = Date(),
    modified: Date = added,
    rank: FractionalIndex = NO_RANK,
    status: NoteStatus = NoteStatus.ACTIVE,
    color: Int = 0,
    pinned: PinnedStatus = defaultPinnedStatusForStatus(status),
    reminder: Reminder? = null
) = Note(id, type, title, content, metadata, added, modified, rank, status, color, pinned, reminder)

fun listNote(
    items: List<ListNoteItem>,
    id: Long = Note.NO_ID,
    title: String = "note",
    added: Date = Date(),
    modified: Date = added,
    rank: FractionalIndex = NO_RANK,
    status: NoteStatus = NoteStatus.ACTIVE,
    color: Int = 0,
    pinned: PinnedStatus = defaultPinnedStatusForStatus(status),
    reminder: Reminder? = null
) = Note(id, NoteType.LIST, title,
    items.joinToString("\n") {
        require('\n' !in it.content)
        it.content
    },
    ListNoteMetadata(items.map { it.checked }), added, modified, rank, status, color, pinned, reminder)

fun assertNoteEquals(
    expected: Note,
    actual: Note,
    dateEpsilon: Long = 1000,
    ignoreId: Boolean = true
) {
    assertTrue((expected.addedDate.time - actual.addedDate.time) < dateEpsilon,
        "Notes have different added dates.")
    assertTrue((expected.lastModifiedDate.time - actual.lastModifiedDate.time) < dateEpsilon,
        "Notes have different last modified dates.")
    assertEquals(expected, actual.copy(
        id = if (ignoreId) expected.id else actual.id,
        addedDate = expected.addedDate,
        lastModifiedDate = expected.lastModifiedDate))
}

val NO_RANK = FractionalIndex.fromBytes(ubyteArrayOf(0x83u, 0xdeu, 0xadu, 0xbeu, 0xefu).asByteArray())

private fun defaultPinnedStatusForStatus(status: NoteStatus) =
    if (status == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN
