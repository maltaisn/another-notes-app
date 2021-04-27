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

package com.maltaisn.notes.ui.labels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.ui.getOrAwaitValue
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        labelsRepo.addLabel(Label(Label.NO_ID, "label0"))
        labelsRepo.addLabel(Label(Label.NO_ID, "label1"))
        labelsRepo.addLabel(Label(Label.NO_ID, "label2"))

        viewModel = LabelViewModel(labelsRepo, SavedStateHandle())
    }

    @Test
    fun `should select all or no labels`() = mainCoroutineRule.runBlockingTest {
        viewModel.selectAll()

        var listItems = viewModel.labelItems.getOrAwaitValue()
        assertTrue(listItems.all { it.checked })
        assertEquals(3, viewModel.labelSelection.getOrAwaitValue())

        viewModel.clearSelection()

        listItems = viewModel.labelItems.getOrAwaitValue()
        assertTrue(listItems.none { it.checked })
        assertEquals(0, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection of label on long click`() = mainCoroutineRule.runBlockingTest {
        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        assertTrue(getLabelItemAt(0).checked)
        assertEquals(1, viewModel.labelSelection.getOrAwaitValue())

        viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)
        assertFalse(getLabelItemAt(0).checked)
        assertEquals(0, viewModel.labelSelection.getOrAwaitValue())
    }

    @Test
    fun `should toggle selection on label on click after first selected`() =
        mainCoroutineRule.runBlockingTest {
            // Select a first label by long click
            viewModel.onLabelItemLongClicked(getLabelItemAt(0), 0)

            viewModel.onLabelItemLongClicked(getLabelItemAt(1), 1)
            assertTrue(getLabelItemAt(1).checked)

            viewModel.onLabelItemClicked(getLabelItemAt(1), 1)
            assertFalse(getLabelItemAt(1).checked)
        }

    @Test
    fun `should update placeholder visibility`() = mainCoroutineRule.runBlockingTest {
        assertFalse(viewModel.placeholderShown.getOrAwaitValue())
        labelsRepo.clearAllData()
        assertTrue(viewModel.placeholderShown.getOrAwaitValue())
    }

    private fun getLabelItemAt(pos: Int) = viewModel.labelItems.value!!.get(pos)
}
