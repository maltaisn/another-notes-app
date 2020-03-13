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

package com.maltaisn.notes.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*


@Entity(tableName = "notes")
data class Note(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "id")
        val id: Long,

        /**
         * Note type, determines the type of metadata.
         */
        @ColumnInfo(name = "type")
        val type: NoteType,

        /**
         * Note title, can be used for search.
         */
        @ColumnInfo(name = "title")
        val title: String,

        /**
         * Note text content, can be used for search.
         */
        @ColumnInfo(name = "content")
        val content: String,

        /**
         * Note metadata, not used for search.
         * Can be `null` if note has no metadata.
         */
        @ColumnInfo(name = "metadata")
        val metadata: String?,

        /**
         * Creation date of the note, in UTC time.
         */
        @ColumnInfo(name = "added_date")
        val addedDate: Date,

        /**
         * Last modification date of the note, in UTC time.
         * Change of [status] changes last modified date too.
         */
        @ColumnInfo(name = "modified_date")
        val lastModifiedDate: Date,

        /**
         * Status of the note, i.e. its location.
         */
        @ColumnInfo(name = "status")
        val status: NoteStatus
)
