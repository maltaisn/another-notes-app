/*
 * Copyright 2023 Nicolas Maltais
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

package com.maltaisn.notes.ui.labels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notes.ui.labels.adapter.LabelListItem
import com.maltaisn.notesshared.MainCoroutineRule
import com.maltaisn.notesshared.model.MockLabelsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelViewModelTest {

    private lateinit var viewModel: LabelViewModel

    private lateinit var labelsRepo: MockLabelsRepository

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        labelsRepo = MockLabelsRepository()
        labelsRepo.addLabel(Label(1, "label0"))
        labelsRepo.addLabel(Label(2, "label1"))
        labelsRepo.addLabel(Label(3, "label2"))
        labelsRepo.addLabel(Label(4, "label3"))
        labelsRepo.addLabel(Label(5, "label4"))

        labelsRepo.addLabelRefs(listOf(
            LabelRef(1, 1),
            LabelRef(1, 5),
            LabelRef(2, 1),
            LabelRef(2, 3),
            LabelRef(2, 4),
            LabelRef(3, 1),
            LabelRef(3, 4),
            LabelRef(3, 5),
        ))

        viewModel = LabelViewModel(labelsRepo, SavedStateHandle())
    }

    @Test
    fun `should show all labels`() = runTest {
        viewModel.start(emptyList())
        assertEquals(listOf(
            LabelListItem(1, labelsRepo.requireLabelById(1), false),
            LabelListItem(4, labelsRepo.requireLabelById(4), false),
            LabelListItem(5, labelsRepo.requireLabelById(5), false),
            LabelListItem(3, labelsRepo.requireLabelById(3), false),
            LabelListItem(2, labelsRepo.requireLabelById(2), false),
        ), viewModel.labelItems.getOrAwaitValue())
    }

    @Test
    fun `should select all or no labels (management)`() = runTest {
        viewModel.start(emptyList())
        viewModel.selectAll()

        var listItems = viewModel.labelItems.getOrAwaitValue()
        assertTrue(listItems.all { it.checked })
        assertEquals(5, viewModel.labelSelection.getOrAwaitValue())

        viewModel.clearSelection()

        listItems = viewModel.labelItems.getOrAwaitValue()
        assertTrue(listItems.none { it.checked })
        assertEquals(0, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection of label on long click (management)`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        assertTrue(getLabelItemAt(0).checked)
        assertEquals(1, viewModel.labelSelection.getOrAwaitValue())

        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        assertFalse(getLabelItemAt(0).checked)
        assertEquals(0, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection of label on icon click (management)`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemIconClicked(getLabelItemAt(0), 0)
        assertTrue(getLabelItemAt(0).checked)
        assertEquals(1, viewModel.labelSelection.getOrAwaitValue())

        viewModel.onLabelItemIconClicked(getLabelItemAt(0), 0)
        assertFalse(getLabelItemAt(0).checked)
        assertEquals(0, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection on label on click after first selected (management)`() =
        runTest {
            // Select a first label by long click
            viewModel.start(emptyList())
            viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)

            viewModel.onLabelItemLongClicked(getLabelItemAt(1), 1)
            assertTrue(getLabelItemAt(1).checked)

            viewModel.onLabelItemClicked(getLabelItemAt(1), 1)
            assertFalse(getLabelItemAt(1).checked)
        }

    @Test
    fun `should update placeholder visibility`() = runTest {
        viewModel.start(emptyList())
        assertFalse(viewModel.placeholderShown.getOrAwaitValue())
        labelsRepo.clearAllData()
        assertTrue(viewModel.placeholderShown.getOrAwaitValue())
    }

    @Test
    fun `should show rename dialog`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemClicked(getLabelItemAt(3), 3)  // label 3
        assertLiveDataEventSent(viewModel.showRenameDialogEvent, 3)
    }

    @Test
    fun `should show rename dialog for selection`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        viewModel.renameSelection()
        assertLiveDataEventSent(viewModel.showRenameDialogEvent, 1)

        // rename label (simulate confirm of rename dialog)
        labelsRepo.updateLabel(labelsRepo.requireLabelById(1).copy(name = "changed label"))
        // renaming should deselect label
        assertEquals(0, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should delete label without showing confirmation`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemLongClicked(getLabelItemAt(4), 4) // label 2
        viewModel.deleteSelectionPre()
        assertNull(labelsRepo.getLabelById(2))
    }

    @Test
    fun `should delete labels showing confirmation before`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        viewModel.onLabelItemLongClicked(getLabelItemAt(1), 1)
        viewModel.deleteSelectionPre()
        assertLiveDataEventSent(viewModel.showDeleteConfirmEvent)
    }

    @Test
    fun `should keep selection after editing label`() = runTest {
        viewModel.start(emptyList())
        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        assertEquals(1, viewModel.labelSelection.getOrAwaitValue())
        labelsRepo.updateLabel(labelsRepo.requireLabelById(1).copy(name = "changed label"))
        assertEquals(1, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should init with subset of labels shared by all notes`() = runTest {
        viewModel.start(listOf(1, 2, 3))
        assertEquals(listOf(
            LabelListItem(1, labelsRepo.requireLabelById(1), true),
            LabelListItem(4, labelsRepo.requireLabelById(4), false),
            LabelListItem(5, labelsRepo.requireLabelById(5), false),
            LabelListItem(3, labelsRepo.requireLabelById(3), false),
            LabelListItem(2, labelsRepo.requireLabelById(2), false),
        ), viewModel.labelItems.getOrAwaitValue())
    }

    @Test
    fun `should update labels on all notes`() = runTest {
        viewModel.start(listOf(1, 2, 3))
        // deselect label 1
        viewModel.onLabelItemClicked(getLabelItemAt(0), 0)
        // select label 3 and 5
        viewModel.onLabelItemClicked(getLabelItemAt(3), 3)
        // test toggling selection through icon click too
        viewModel.onLabelItemIconClicked(getLabelItemAt(2), 2)

        viewModel.setNotesLabels()

        assertEquals(setOf(3L, 5L), labelsRepo.getLabelIdsForNote(1).toSet())
        assertEquals(setOf(3L, 5L), labelsRepo.getLabelIdsForNote(2).toSet())
        assertEquals(setOf(3L, 5L), labelsRepo.getLabelIdsForNote(3).toSet())

        assertLiveDataEventSent(viewModel.exitEvent)
    }

    @Test
    fun `should select newly created label (case 1)`() = runTest {
        // Case 1: list gets updated before call to select it
        viewModel.start(listOf(1))
        val label = Label(10, "new label")
        labelsRepo.addLabel(label)
        viewModel.selectNewLabel(label)
        assertTrue(getLabelItemAt(5).checked)
    }

    @Test
    fun `should select newly created label (case 2)`() = runTest {
        // Case 2: list gets updated after call to select it
        viewModel.start(listOf(1))
        val label = Label(10, "new label")
        viewModel.selectNewLabel(label)
        labelsRepo.addLabel(label)
        assertTrue(getLabelItemAt(5).checked)
    }

    private fun getLabelItemAt(pos: Int) = viewModel.labelItems.value!![pos]
}
