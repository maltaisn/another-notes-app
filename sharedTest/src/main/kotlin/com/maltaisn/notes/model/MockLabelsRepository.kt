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
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteWithLabels
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

/**
 * Implementation of the labels repository that stores data itself instead of relying on DAOs.
 * This implementation should work almost exactly like [DefaultLabelsRepository].
 */
class MockLabelsRepository : LabelsRepository {

    private val labels = mutableMapOf<Long, Label>()

    // list of label IDs mapped by note
    private val labelRefs = mutableMapOf<Long, MutableSet<Long>>()

    // list of note IDs mapped by label
    private val inverseLabelRefs = mutableMapOf<Long, MutableSet<Long>>()

    private var lastLabelId = 0L

    val changeFlow = MutableSharedFlow<Unit>(replay = 1)
    val refsChangeFlow = MutableSharedFlow<Unit>(replay = 1)

    /**
     * Add label without notifying change flow.
     */
    private fun addLabelInternal(label: Label) =
        if (label.id != Label.NO_ID) {
            labels[label.id] = label
            if (label.id > lastLabelId) {
                lastLabelId = label.id
            }
            label.id
        } else {
            lastLabelId++
            labels[lastLabelId] = label.copy(id = lastLabelId)
            lastLabelId
        }

    /** Non-suspending version of [insertLabel]. */
    fun addLabel(label: Label): Long {
        val id = addLabelInternal(label)
        changeFlow.tryEmit(Unit)
        return id
    }

    override suspend fun insertLabel(label: Label): Long {
        val id = addLabel(label)
        changeFlow.emit(Unit)
        return id
    }

    override suspend fun updateLabel(label: Label) {
        require(label.id in labels) { "Cannot update non-existent label" }
        insertLabel(label)
    }

    private fun deleteLabelInternal(id: Long) {
        labels -= id
        inverseLabelRefs -= id
        for (refs in labelRefs.values) {
            refs -= id
        }
    }

    override suspend fun deleteLabel(label: Label) {
        deleteLabelInternal(label.id)
        refsChangeFlow.emit(Unit)
        changeFlow.emit(Unit)
    }

    override suspend fun deleteLabels(labels: List<Label>) {
        for (label in labels) {
            deleteLabelInternal(label.id)
        }
        refsChangeFlow.emit(Unit)
        changeFlow.emit(Unit)
    }

    override suspend fun getLabelById(id: Long) = labels[id]

    fun requireLabelById(id: Long) = labels.getOrElse(id) {
        error("No label with ID $id")
    }

    override suspend fun getLabelByName(name: String) = labels.values.find { it.name == name }

    override suspend fun insertLabelRefs(refs: List<LabelRef>) {
        addLabelRefs(refs)
    }

    override suspend fun deleteLabelRefs(refs: List<LabelRef>) {
        for (ref in refs) {
            labelRefs[ref.noteId]?.remove(ref.labelId)
            inverseLabelRefs[ref.labelId]?.remove(ref.noteId)
        }
        refsChangeFlow.emit(Unit)
    }

    override suspend fun getLabelIdsForNote(noteId: Long) =
        labelRefs[noteId].orEmpty().toList()

    fun getNotesForLabelId(labelId: Long) =
        inverseLabelRefs[labelId].orEmpty().toList()

    fun getNoteWithLabels(note: Note) = NoteWithLabels(note,
        labelRefs[note.id].orEmpty().map { requireLabelById(it) })

    // Non suspending version for initialization
    fun addLabelRefs(refs: List<LabelRef>) {
        for (ref in refs) {
            labelRefs.getOrPut(ref.noteId) { mutableSetOf() } += ref.labelId
            inverseLabelRefs.getOrPut(ref.labelId) { mutableSetOf() } += ref.noteId
        }
        refsChangeFlow.tryEmit(Unit)
    }

    override suspend fun countLabelRefs(labelId: Long) =
        inverseLabelRefs[labelId].orEmpty().size.toLong()

    override suspend fun clearAllData() {
        labels.clear()
        labelRefs.clear()
        inverseLabelRefs.clear()
        lastLabelId = 0
        changeFlow.emit(Unit)
        refsChangeFlow.emit(Unit)
    }

    suspend fun clearAllLabelRefs() {
        labelRefs.clear()
        inverseLabelRefs.clear()
        refsChangeFlow.emit(Unit)
    }

    override fun getAllLabelsByUsage() = changeFlow.map {
        labels.values.asSequence()
            .sortedWith(compareByDescending<Label> { inverseLabelRefs[it.id].orEmpty().size }
                .thenBy { it.name })
            .toList()
    }
}
