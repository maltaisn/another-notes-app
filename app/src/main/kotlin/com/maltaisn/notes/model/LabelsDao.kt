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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.maltaisn.notes.model.entity.Label
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: Label): Long

    @Update
    suspend fun update(label: Label)

    @Delete
    suspend fun delete(label: Label)

    /**
     * Used for clearing all data.
     */
    @Query("DELETE FROM labels")
    suspend fun clear()

    /**
     * Get all labels in database
     * Used for viewing labels and exporting data.
     */
    @Query("SELECT * FROM labels")
    fun getAll(): Flow<List<Label>>

    /**
     * Get a label by its ID. Returns `null` if label doesn't exist.
     */
    @Query("SELECT * FROM labels WHERE id == :id")
    suspend fun getById(id: Long): Label?

    /**
     * Get a label by its name, or `null` if none exists. Name must match exactly.
     * Used to ensure name uniqueness and for searching by label.
     */
    @Query("SELECT * FROM labels WHERE name == :name")
    suspend fun getLabelByName(name: String): Label?

}
