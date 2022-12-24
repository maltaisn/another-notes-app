/*
 * Copyright 2022 Nicolas Maltais
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

import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import kotlinx.coroutines.flow.Flow

interface LabelsRepository {

    suspend fun insertLabel(label: Label): Long

    suspend fun updateLabel(label: Label)

    suspend fun deleteLabel(label: Label)
    suspend fun deleteLabels(labels: List<Label>)

    suspend fun getLabelById(id: Long): Label?
    suspend fun getLabelByName(name: String): Label?

    suspend fun insertLabelRefs(refs: List<LabelRef>)
    suspend fun deleteLabelRefs(refs: List<LabelRef>)
    suspend fun getLabelIdsForNote(noteId: Long): List<Long>
    suspend fun countLabelRefs(labelId: Long): Long

    fun getAllLabelsByUsage(): Flow<List<Label>>

    suspend fun clearAllData()
}
