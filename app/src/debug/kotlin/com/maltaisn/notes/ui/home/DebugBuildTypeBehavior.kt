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

package com.maltaisn.notes.ui.home

import com.maltaisn.notes.DebugUtils
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.navigation.HomeDestination
import javax.inject.Inject

class DebugBuildTypeBehavior @Inject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
) : BuildTypeBehavior {

    override suspend fun doExtraAction(viewModel: HomeViewModel) {
        // Add a few random notes of the currently selected status.
        val destination = viewModel.currentDestination
        if (destination is HomeDestination.Status) {
            repeat(3) {
                notesRepository.insertNote(DebugUtils.getRandomNote(destination.status))
            }

            // For performance testing
//            val labels = (1L..100L).map { Label(it, UUID.randomUUID().toString().substring(0, 9)) }
//            for (label in labels) {
//                labelsRepository.insertLabel(label)
//            }
//            for (status in NoteStatus.values()) {
//                repeat(300) {
//                    val id = notesRepository.insertNote(DebugUtils.getRandomNote(status))
//                    val noteLabels = labels.shuffled().subList(0, Random.nextInt(10))
//                    labelsRepository.insertLabelRefs(noteLabels.map { LabelRef(id, it.id) })
//                }
//            }
        } else if (destination is HomeDestination.Labels) {
            repeat(3) {
                val id = notesRepository.insertNote(DebugUtils.getRandomNote(NoteStatus.ACTIVE))
                labelsRepository.insertLabelRefs(listOf(LabelRef(id, destination.label.id)))
            }
        }
    }
}
