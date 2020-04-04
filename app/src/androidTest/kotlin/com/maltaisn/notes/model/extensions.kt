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

package com.maltaisn.notes.model

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import java.util.*


fun atestNote(
        id: Long = Note.NO_ID,
        uuid: String = Note.generateNoteUuid(),
        type: NoteType = NoteType.TEXT,
        title: String = "note",
        content: String = "content",
        metadata: String? = null,
        added: Date = Date(),
        modified: Date = Date(),
        status: NoteStatus = NoteStatus.ACTIVE
) = Note(id, uuid, type, title, content, metadata, added, modified, status)
