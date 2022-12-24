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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultLabelsRepository @Inject constructor(
    private val labelsDao: LabelsDao,
) : LabelsRepository {

    // Data modification methods are wrapped in non-cancellable context
    // so that calling them in onPause for example won't cancel the transaction on the
    // subsequent onDestroy call, which cancels the coroutine scope.

    override suspend fun insertLabel(label: Label) = withContext(NonCancellable) {
        labelsDao.insert(label)
    }

    override suspend fun updateLabel(label: Label) = withContext(NonCancellable) {
        labelsDao.update(label)
    }

    override suspend fun deleteLabel(label: Label) {
        labelsDao.delete(label)
    }

    override suspend fun deleteLabels(labels: List<Label>) {
        labelsDao.deleteAll(labels)
    }

    override suspend fun getLabelById(id: Long) = labelsDao.getById(id)

    override suspend fun getLabelByName(name: String) = labelsDao.getLabelByName(name)

    override suspend fun insertLabelRefs(refs: List<LabelRef>) = labelsDao.insertRefs(refs)

    override suspend fun deleteLabelRefs(refs: List<LabelRef>) = labelsDao.deleteRefs(refs)

    override suspend fun getLabelIdsForNote(noteId: Long) =
        labelsDao.getLabelIdsForNote(noteId)

    override suspend fun countLabelRefs(labelId: Long) = labelsDao.countRefs(labelId)

    override fun getAllLabelsByUsage() = labelsDao.getAllByUsage()

    override suspend fun clearAllData() {
        labelsDao.clear()
    }
}
