/*
 * Copyright 2021 Nicolas Maltais
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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

class DefaultJsonExporter @Inject constructor(
    private val notesDao: NotesDao,
    private val labelsDao: LabelsDao,
    private val json: Json,
) : JsonExporter {

    override suspend fun exportJsonData() = json.encodeToString(JsonObject.serializer(),
        buildJsonObject {
            // Put all notes under a "notes" entry, with their labels.
            put("notes", buildJsonObject {
                val notes = notesDao.getAll()
                for (note in notes) {
                    val element = buildJsonObject {
                        for ((key, value) in (json.encodeToJsonElement(note.note) as JsonObject)) {
                            put(key, value)
                        }
                        putJsonArray("labels") {
                            for (label in note.labels) {
                                add(label.id)
                            }
                        }
                    }
                    put(note.note.id.toString(), element)
                }
            })

            // Put all labels under a "labels" entry.
            put("labels", buildJsonObject {
                val labels = labelsDao.getAll()
                for (label in labels) {
                    put(label.id.toString(), json.encodeToJsonElement(label))
                }
            })
        })

}
