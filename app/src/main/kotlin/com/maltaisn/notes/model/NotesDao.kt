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

import androidx.room.*
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.flow.Flow


@Dao
interface NotesDao {

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Update
    suspend fun updateAll(notes: List<Note>)

    @Delete
    suspend fun delete(note: Note)

    @Delete
    suspend fun deleteAll(notes: List<Note>)

    @Query("DELETE FROM notes WHERE uuid == :uuid")
    suspend fun delete(uuid: String)

    @Query("DELETE FROM notes WHERE status == :status")
    suspend fun deleteByStatus(status: NoteStatus)

    @Query("SELECT * FROM notes WHERE id == :id")
    suspend fun getById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE uuid == :uuid")
    suspend fun getByUuid(uuid: String): Note?

    @Query("SELECT id FROM notes WHERE uuid == :uuid")
    suspend fun getIdByUuid(uuid: String): Long?

    @Query("SELECT * FROM notes WHERE status == :status ORDER BY modified_date DESC")
    fun getByStatus(status: NoteStatus): Flow<List<Note>>

    @Query("""SELECT * FROM notes JOIN notes_fts ON notes_fts.rowid == notes.id
        WHERE notes_fts MATCH :query AND status != 2
        ORDER BY status ASC, modified_date DESC""")
    fun search(query: String): Flow<List<Note>>

}
