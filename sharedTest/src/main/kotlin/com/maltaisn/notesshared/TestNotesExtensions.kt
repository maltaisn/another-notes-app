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

@file:Suppress("LongParameterList")

package com.maltaisn.notesshared

import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Date

fun testNote(
    id: Long = Note.NO_ID,
    type: NoteType = NoteType.TEXT,
    title: String = "note",
    content: String = "content",
    metadata: NoteMetadata = BlankNoteMetadata,
    added: Date = Date(),
    modified: Date = added,
    status: NoteStatus = NoteStatus.ACTIVE,
    pinned: PinnedStatus = defaultPinnedStatusForStatus(status),
    reminder: Reminder? = null
) = Note(id, type, title, content, metadata, added, modified, status, pinned, reminder)

fun listNote(
    items: List<ListNoteItem>,
    id: Long = Note.NO_ID,
    title: String = "note",
    added: Date = Date(),
    modified: Date = added,
    status: NoteStatus = NoteStatus.ACTIVE,
    pinned: PinnedStatus = defaultPinnedStatusForStatus(status),
    reminder: Reminder? = null
) = Note(id, NoteType.LIST, title,
    items.joinToString("\n") {
        require('\n' !in it.content)
        it.content
    },
    ListNoteMetadata(items.map { it.checked }), added, modified, status, pinned, reminder)

fun assertNoteEquals(
    expected: Note,
    actual: Note,
    dateEpsilon: Long = 1000,
    ignoreId: Boolean = true
) {
    assertTrue("Notes have different added dates.",
        (expected.addedDate.time - actual.addedDate.time) < dateEpsilon)
    assertTrue("Notes have different last modified dates.",
        (expected.lastModifiedDate.time - actual.lastModifiedDate.time) < dateEpsilon)
    assertEquals(expected, actual.copy(
        id = if (ignoreId) expected.id else actual.id,
        addedDate = expected.addedDate,
        lastModifiedDate = expected.lastModifiedDate))
}

private fun defaultPinnedStatusForStatus(status: NoteStatus) =
    if (status == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN
