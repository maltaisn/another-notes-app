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
        @SerialName("uuid")
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
        require (type != NoteType.LIST || metadata != null)
    }

    fun getListItems(json: Json): List<ListNoteItem> {
        check (type == NoteType.LIST) { "Cannot get list items for non-list note." }

        val checked = json.parse(ListNoteMetadata.serializer(), metadata!!)
        val items = content.split('\n')
        check (checked.checked.size == items.size) { "Invalid list note data." }

        return items.mapIndexed { i, text ->
            ListNoteItem(text, checked.checked[i])
        }
    }

}

data class ListNoteItem(val content: String, val checked: Boolean)
