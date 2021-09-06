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

@file:UseSerializers(DateTimeConverter::class, NoteTypeConverter::class,
    NoteStatusConverter::class, NoteMetadataConverter::class, PinnedStatusConverter::class)

package com.maltaisn.notes.model

import androidx.room.ColumnInfo
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.converter.NoteMetadataConverter
import com.maltaisn.notes.model.converter.NoteStatusConverter
import com.maltaisn.notes.model.converter.NoteTypeConverter
import com.maltaisn.notes.model.converter.PinnedStatusConverter
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date
import javax.inject.Inject

class DefaultJsonManager @Inject constructor(
    private val notesDao: NotesDao,
    private val labelsDao: LabelsDao,
    private val json: Json,
    private val reminderAlarmManager: ReminderAlarmManager,
) : JsonManager {

    override suspend fun exportJsonData(): String {
        // Map notes by ID, with labels
        val notesMap = mutableMapOf<Long, NoteSurrogate>()
        val notesList = notesDao.getAll()
        for (noteWithLabels in notesList) {
            val note = noteWithLabels.note
            notesMap[note.id] = NoteSurrogate(note.type, note.title, note.content,
                note.metadata, note.addedDate, note.lastModifiedDate, note.status, note.pinned,
                note.reminder, noteWithLabels.labels.map { it.id })
        }

        // Map labels by ID
        val labelsMap = mutableMapOf<Long, Label>()
        val labelsList = labelsDao.getAll()
        for (label in labelsList) {
            labelsMap[label.id] = label
        }

        // Encode to JSON and insert labels afterwards
        val notesData = NotesData(VERSION, notesMap, labelsMap)
        return json.encodeToString(notesData)
    }

    override suspend fun importJsonData(data: String): ImportResult {
        val notesData: NotesData = try {
            json.decodeFromString(data)
        } catch (e: BadDataException) {
            // could happen if user imported data from future version, which has incompatibilities.
            return ImportResult.BAD_DATA
        } catch (e: Exception) {
            // bad json structure, missing required fields, field has bad value, etc.
            return ImportResult.BAD_FORMAT
        }

        if (notesData.version < FIRST_VERSION) {
            // first version is 3, this data is clearly wrong.
            return ImportResult.BAD_DATA
        }

        // Import all data
        val newLabelsMap = importLabels(notesData)
        importNotes(notesData, newLabelsMap)

        // Update all reminders
        reminderAlarmManager.updateAllAlarms()

        return if (notesData.version > VERSION) {
            // data comes from future version of app
            ImportResult.FUTURE_VERSION
        } else {
            ImportResult.SUCCESS
        }
    }

    private suspend fun importLabels(notesData: NotesData): Map<Long, Long> {
        val existingLabels = labelsDao.getAll()
        val existingLabelsIdMap = existingLabels.associateBy { it.id }
        val existingLabelsNameMap = existingLabels.associateBy { it.name }
        val newLabelsMap = mutableMapOf<Long, Long>()
        for ((id, label) in notesData.labels) {
            val name = label.name.trim().replace("""\s+""".toRegex(), " ")
            val existingLabelById = existingLabelsIdMap[id]
            if (existingLabelById != null) {
                // Label ID already exists, if name doesn't match assume this is a different label.
                if (name != existingLabelById.name) {
                    newLabelsMap[id] = labelsDao.insert(Label(Label.NO_ID, name))
                } else {
                    newLabelsMap[id] = id
                }
            } else {
                val existingLabelByName = existingLabelsNameMap[name]
                val labelName = if (existingLabelByName != null) {
                    // Label name already exists, create a new one.
                    var newName: String
                    var num = 2
                    do {
                        newName = "$name ($num)"
                        num++
                    } while (newName in existingLabelsNameMap)
                    newName
                } else {
                    name
                }
                newLabelsMap[id] = labelsDao.insert(Label(id, labelName))
            }
        }
        return newLabelsMap
    }

    private suspend fun importNotes(notesData: NotesData, newLabelsMap: Map<Long, Long>) {
        val existingNotes = notesDao.getAll().asSequence().map { it.note }.associateBy { it.id }
        val labelRefs = mutableListOf<LabelRef>()
        for ((id, ns) in notesData.notes) {
            var noteId = id
            val note = Note(id, ns.type, ns.title, ns.content, ns.metadata, ns.addedDate,
                ns.lastModifiedDate, ns.status, ns.pinned, ns.reminder)
            val existingNote = existingNotes[noteId]
            when {
                existingNote == null -> {
                    notesDao.insert(note)
                }
                existingNote.addedDate == note.addedDate -> {
                    // existing note has same creation date as the data, assume this is the same
                    // same that was exported in the first place.
                    notesDao.update(note)
                }
                else -> {
                    // ID clash, assign new ID.
                    noteId = notesDao.insert(note.copy(id = Note.NO_ID))
                }
            }

            // Add label references, remapping labels appropriately and discarding unresolved label IDs.
            labelRefs += ns.labels.mapNotNull { labelId ->
                newLabelsMap[labelId]?.let { LabelRef(noteId, it) }
            }
        }
        labelsDao.insertRefs(labelRefs)
    }

    enum class ImportResult {
        SUCCESS,
        BAD_FORMAT,
        BAD_DATA,
        FUTURE_VERSION,
    }

    companion object {
        private const val VERSION = 4
        private const val FIRST_VERSION = 3
    }
}

/**
 * Same fields as [Note], minus redundant [Note.id] and
 * adding [labels] to store label references.
 */
@Serializable
private data class NoteSurrogate(
    @SerialName("type")
    val type: NoteType,
    @SerialName("title")
    val title: String,
    @SerialName("content")
    val content: String,
    @SerialName("metadata")
    val metadata: NoteMetadata,
    @SerialName("added")
    val addedDate: Date,
    @SerialName("modified")
    val lastModifiedDate: Date,
    @SerialName("status")
    val status: NoteStatus,
    @ColumnInfo(name = "pinned")
    @SerialName("pinned")
    val pinned: PinnedStatus,
    @SerialName("reminder")
    val reminder: Reminder? = null,
    @SerialName("labels")
    val labels: List<Long> = emptyList(),
)

@Serializable
private data class NotesData(
    @SerialName("version")
    val version: Int,
    @SerialName("notes")
    val notes: Map<Long, NoteSurrogate> = emptyMap(),
    @SerialName("labels")
    val labels: Map<Long, Label> = emptyMap()
)
