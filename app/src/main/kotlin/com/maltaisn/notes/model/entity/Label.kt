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

package com.maltaisn.notes.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Entity(tableName = "labels")
data class Label(
    /**
     * Label ID in the database.
     * ID is transient during serialization since labels are mapped by ID in JSON,
     * so repeating this field would be superfluous.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @Transient
    val id: Long = NO_ID,

    /**
     * Label name, cannot be blank.
     */
    @ColumnInfo(name = "name", index = true)
    val name: String,
) {

    init {
        require(name.isNotBlank()) { "Blank label name" }
    }

    companion object {
        const val NO_ID = 0L
    }

}

/**
 * A label reference by a note.
 * When a label is deleted, the reference is deleted too.
 */
@Entity(
    tableName = "label_refs",
    primaryKeys = ["noteId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Label::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ]
)
data class LabelRef(
    @ColumnInfo(name = "noteId")
    val noteId: Long,

    @ColumnInfo(name = "labelId")
    val labelId: Long,
)
