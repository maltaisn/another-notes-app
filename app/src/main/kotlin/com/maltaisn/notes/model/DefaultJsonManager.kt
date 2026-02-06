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

@file:UseSerializers(DateTimeConverter::class, NoteTypeConverter::class,
    NoteStatusConverter::class, NoteMetadataConverter::class, PinnedStatusConverter::class)

package com.maltaisn.notes.model

import android.util.Base64
import com.maltaisn.notes.model.JsonManager.ImportResult
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.converter.NoteMetadataConverter
import com.maltaisn.notes.model.converter.NoteStatusConverter
import com.maltaisn.notes.model.converter.NoteTypeConverter
import com.maltaisn.notes.model.converter.PinnedStatusConverter
import com.maltaisn.notes.model.entity.FractionalIndex
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Date
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class DefaultJsonManager @Inject constructor(
    private val notesDao: NotesDao,
    private val labelsDao: LabelsDao,
    private val json: Json,
    private val reminderAlarmManager: ReminderAlarmManager,
    private val prefs: PrefsManager,
) : JsonManager {

    override suspend fun exportJsonData(): String {
        // Map notes by ID, with labels
        val notesMap = mutableMapOf<Long, NoteSurrogate>()
        val notesList = notesDao.getAll()
        for (noteWithLabels in notesList) {
            val note = noteWithLabels.note
            notesMap[note.id] = NoteSurrogate(note.type, note.title, note.content,
                note.metadata, note.addedDate, note.lastModifiedDate,
                note.rank, note.status, note.color, note.pinned, note.reminder,
                noteWithLabels.labels.map { it.id })
        }

        // Map labels by ID
        val labelsMap = mutableMapOf<Long, Label>()
        val labelsList = labelsDao.getAll()
        for (label in labelsList) {
            labelsMap[label.id] = label
        }

        // Encode to JSON and insert labels afterwards
        val notesData = NotesData(VERSION, notesMap, labelsMap)

        // Handle optional encryption
        return if (prefs.shouldEncryptExportedData) {
            json.encodeToString(encryptNotesData(notesData))
        } else {
            json.encodeToString(notesData)
        }
    }

    private suspend fun encryptNotesData(notesData: NotesData): EncryptedNotesData {
        // Load the Android key store
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }
        // Retrieve the encryption key
        val keyStoreKey = keyStore.getKey(EXPORT_ENCRYPTION_KEY_ALIAS, null)

        // Initialize cipher object
        val cipher = Cipher.getInstance(EXPORT_ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keyStoreKey)

        // Encrypt notesData
        val ciphertext = cipher.doFinal(json.encodeToString(notesData).toByteArray(Charsets.UTF_8))

        return EncryptedNotesData(
            salt = prefs.encryptedExportKeyDerivationSalt,
            nonce = Base64.encodeToString(cipher.iv, BASE64_FLAGS),
            ciphertext = Base64.encodeToString(ciphertext, BASE64_FLAGS)
        )
    }

    override suspend fun importJsonData(data: String, importKey: SecretKey?): ImportResult {
        // JSON can either describe an EncryptedNotesData object or a NotesData object.
        val jsonData: String = try {
            val encryptedNotesData: EncryptedNotesData = json.decodeFromString(data)

            // Key needs to be derived in order to decrypt the backup file
            if (importKey == null) {
                prefs.encryptedImportKeyDerivationSalt = encryptedNotesData.salt
                return ImportResult.KEY_MISSING_OR_INCORRECT
            }

            // Get GCM nonce
            val nonce = Base64.decode(encryptedNotesData.nonce, BASE64_FLAGS)
            val gcmParameterSpec = GCMParameterSpec(128, nonce)

            // Initialize the cipher object
            val cipher = Cipher.getInstance(EXPORT_ENCRYPTION_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, importKey, gcmParameterSpec)

            val ciphertext = Base64.decode(encryptedNotesData.ciphertext, BASE64_FLAGS)
            val plaintext = try {
                cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            } catch (e: AEADBadTagException) {
                // This mainly occurs when the user has entered the wrong password
                return ImportResult.KEY_MISSING_OR_INCORRECT
            } catch (e: Exception) {
                return ImportResult.BAD_DATA
            }
            plaintext
        } catch (e: Exception) {
            // Data is probably not encrypted.
            data
        }

        val notesData: NotesData = try {
            json.decodeFromString(jsonData)
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
                if (existingLabelByName != null) {
                    // Label name already exists, create a new one.
                    var newName: String
                    var num = 2
                    do {
                        newName = "$name ($num)"
                        num++
                    } while (newName in existingLabelsNameMap)
                    newLabelsMap[id] = labelsDao.insert(Label(id, newName, false))
                } else {
                    newLabelsMap[id] = labelsDao.insert(Label(id, name, label.hidden))
                }
            }
        }
        return newLabelsMap
    }

    private suspend fun importNotes(notesData: NotesData, newLabelsMap: Map<Long, Long>) {
        val existingNotes = notesDao.getAll().associateBy { it.note.id }
        val allRanks = existingNotes.values.asSequence().map { it.note.rank }.toMutableSet()
        val labelRefs = mutableListOf<LabelRef>()

        for ((id, ns) in notesData.notes) {
            var noteId = id

            var rank = ns.rank
            if (rank == null || rank in allRanks) {
                rank = FractionalIndex.insert(null, notesDao.getLowestNoteRank())
                allRanks += rank
            }

            val newNote = Note(id, ns.type, ns.title, ns.content, ns.metadata, ns.addedDate,
                ns.lastModifiedDate, rank, ns.status, ns.color, ns.pinned, ns.reminder)
            val oldNote = existingNotes[noteId]

            // Remap labels appropriately and discard unresolved label IDs.
            var labelIds = ns.labels.mapNotNull { newLabelsMap[it] }

            when {
                oldNote == null -> {
                    notesDao.insert(newNote)
                }
                oldNote.note.addedDate == newNote.addedDate &&
                        oldNote.note.lastModifiedDate == newNote.lastModifiedDate -> {
                    // existing note has same added and modified date as the data, assume this is the same
                    // same that was exported in the first place, unmodified since.
                    // changing the reminder or labels doesn't affect last modified date so merge them explicitly.
                    val mergedNote = mergeNotes(oldNote.note, newNote)
                    if (mergedNote != null) {
                        labelIds = (labelIds union oldNote.labels.map { it.id }).toList()
                        notesDao.update(mergedNote)
                    } else {
                        noteId = notesDao.insert(newNote.copy(id = Note.NO_ID))
                    }
                }
                else -> {
                    // ID clash, assign new ID.
                    noteId = notesDao.insert(newNote.copy(id = Note.NO_ID))
                }
            }

            labelRefs += labelIds.map { LabelRef(noteId, it) }
        }
        labelsDao.insertRefs(labelRefs)
    }

    private fun mergeNotes(old: Note, new: Note): Note? {
        val reminder = when {
            old.reminder == null && new.reminder != null -> new.reminder
            old.reminder != null && new.reminder == null -> old.reminder
            old.reminder != null && !compareReminders(old.reminder, new.reminder!!) -> {
                // Old and new notes have different reminders, do not merge to avoid losing one or the other.
                return null
            }
            else -> null
        }

        return new.copy(reminder = reminder)
    }

    private fun compareReminders(old: Reminder, new: Reminder) =
        old.start == new.start && old.recurrence == new.recurrence

    companion object {
        /**
         * Try to keep the version number in sync with the database version.
         * Version history:
         * - 3: initial version
         * - 4: added "hidden" attribute on label
         * - 5: added "rank" attribute on note
         */
        private const val VERSION = 5
        private const val FIRST_VERSION = 3

        private const val EXPORT_ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        private const val EXPORT_ENCRYPTION_KEY_ALIAS = "export_key"

        private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING
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
    @SerialName("rank")
    val rank: FractionalIndex? = null,
    @SerialName("status")
    val status: NoteStatus,
    @SerialName("color")
    val color: Int = 0,
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

@Serializable
private data class EncryptedNotesData(
    @SerialName("salt")
    val salt: String,
    @SerialName("nonce")
    val nonce: String,
    @SerialName("ciphertext")
    val ciphertext: String
)