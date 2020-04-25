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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maltaisn.notes.model.entity.DeletedNote


/**
 * This DAO is used for syncing, in order to send to server which notes were deleted from trash.
 */
@Dao
interface DeletedNotesDao {

    @Insert
    suspend fun insert(note: DeletedNote)

    @Insert
    suspend fun insertAll(notes: List<DeletedNote>)

    /**
     * Used for clearing all data.
     */
    @Query("DELETE FROM deleted_notes")
    suspend fun clear()

    /**
     * Used for syncing to get which UUIDs aren't synced.
     */
    @Query("SELECT uuid FROM deleted_notes WHERE synced == 0")
    suspend fun getNotSyncedUuids(): List<String>

    /**
     * Used after sync to set all synced flags to `true`.
     */
    @Query("UPDATE deleted_notes SET synced = :synced")
    suspend fun setSyncedFlag(synced: Boolean)

}
