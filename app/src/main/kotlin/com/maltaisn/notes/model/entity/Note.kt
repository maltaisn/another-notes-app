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

@file:UseSerializers(DateTimeConverter::class, NoteTypeConverter::class, NoteStatusConverter::class)

package com.maltaisn.notes.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.converter.NoteStatusConverter
import com.maltaisn.notes.model.converter.NoteTypeConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.util.*


@Serializable
@Entity(tableName = "notes")
data class Note(
        @PrimaryKey(autoGenerate = true)
        @Transient
        @ColumnInfo(name = "id")
        val id: Long = 0,

        /**
         * UUID used to identify the note uniquely.
         */
        @ColumnInfo(name = "uuid", index = true)
        val uuid: String,

        /**
         * Note type, determines the type of metadata.
         */
        @ColumnInfo(name = "type")
        @SerialName("type")
        val type: NoteType = NoteType.TEXT,

        /**
         * Note title, can be used for search.
         */
        @ColumnInfo(name = "title")
        @SerialName("title")
        val title: String = "",

        /**
         * Note text content, can be used for search.
         */
        @ColumnInfo(name = "content")
        @SerialName("content")
        val content: String = "",

        /**
         * Note metadata, not used for search.
         * Can be `null` if note has no metadata.
         */
        @ColumnInfo(name = "metadata")
        @SerialName("metadata")
        val metadata: String? = null,

        /**
         * Creation date of the note, in UTC time.
         */
        @ColumnInfo(name = "added_date")
        @SerialName("added")
        val addedDate: Date = Date(),

        /**
         * Last modification date of the note, in UTC time.
         * Change of [status] changes last modified date too.
         */
        @ColumnInfo(name = "modified_date")
        @SerialName("modified")
        val lastModifiedDate: Date,

        /**
         * Status of the note, i.e. its location.
         */
        @ColumnInfo(name = "status")
        @SerialName("status")
        val status: NoteStatus
) {

    init {
        require(type != NoteType.LIST || metadata != null)
    }

    /**
     * Returns true if note has no content.
     * Metadata is not taken into account.
     */
    val isBlank: Boolean
        get() = title.isBlank() && content.isBlank()

    /**
     * If not is a list note, parse content and metadata into a list of items.
     */
    fun getListItems(json: Json): List<ListNoteItem> {
        check(type == NoteType.LIST) { "Cannot get list items for non-list note." }

        val checked = json.parse(ListNoteMetadata.serializer(), metadata!!)
        val items = content.split('\n')
        if (items.size == 1 && checked.checked.isEmpty()) {
            // No items
            return emptyList()
        }

        check(checked.checked.size == items.size) { "Invalid list note data." }

        return items.mapIndexed { i, text ->
            ListNoteItem(text, checked.checked[i])
        }
    }

    fun convertToType(type: NoteType, json: Json): Note {
        if (this.type == type) {
            return this
        }

        val lines = content.split('\n')

        val content: String
        val metadata: String?
        when (type) {
            NoteType.TEXT -> {
                // Append a bullet point to each line of content.
                content = if (lines.all { it.isBlank() }) {
                    ""
                } else {
                    buildString {
                        for (line in lines) {
                            append(DEFAULT_BULLET_CHAR)
                            append(' ')
                            append(line)
                            append('\n')
                        }
                        deleteCharAt(lastIndex)
                    }
                }
                metadata = null
            }
            NoteType.LIST -> {
                // Convert each list item to a text line.
                content = if (lines.all { it.isNotEmpty() && it.first() in BULLET_CHARS }) {
                    // All lines start with a bullet point, remove them.
                    buildString {
                        for (line in lines) {
                            append(line.substring(1).trim())
                            append('\n')
                        }
                        deleteCharAt(lastIndex)
                    }
                } else {
                    this.content
                }
                metadata = json.stringify(ListNoteMetadata.serializer(),
                        ListNoteMetadata(List(lines.size) { false }))
            }
        }
        return Note(id, uuid, type, title, content, metadata, addedDate, lastModifiedDate, status)
    }

    fun asText(json: Json): String {
        val textNote = convertToType(NoteType.TEXT, json)
        return buildString {
            if (title.isNotBlank()) {
                append(textNote.title)
                append('\n')
            }
            append(textNote.content)
        }
    }

    companion object {
        const val NO_ID = 0L

        const val BULLET_CHARS = "-+*•–"
        const val DEFAULT_BULLET_CHAR = "-"


        fun getCopiedNoteTitle(currentTitle: String, untitledName: String, copySuffix: String): String {
            val match = "^(.*) - $copySuffix(?:\\s+([1-9]\\d*))?$".toRegex().find(currentTitle)
            return when {
                match != null -> {
                    val name = match.groupValues[1]
                    val number = (match.groupValues[2].toIntOrNull() ?: 1) + 1
                    "$name - $copySuffix $number"
                }
                currentTitle.isBlank() -> "$untitledName - $copySuffix"
                else -> "$currentTitle - $copySuffix"
            }
        }

        fun generateNoteUuid() = UUID.randomUUID().toString().replace("-", "")
    }
}

data class ListNoteItem(val content: String, val checked: Boolean)
