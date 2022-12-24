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

package com.maltaisn.notes.ui.labels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.model.MockLabelsRepository
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class LabelEditViewModelTest {

    private lateinit var viewModel: LabelEditViewModel

    private lateinit var labelsRepo: MockLabelsRepository

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        labelsRepo = MockLabelsRepository()
        labelsRepo.addLabel(Label(1, "label0", false))
        labelsRepo.addLabel(Label(2, "label1", true))

        viewModel = LabelEditViewModel(labelsRepo, SavedStateHandle())
    }

    @Test
    fun `should add label`() = runTest {
        viewModel.start(Label.NO_ID)
        assertLiveDataEventSent(viewModel.setLabelEvent, Label(Label.NO_ID, "", false))
        viewModel.onNameChanged("      \t     my label    \t    ")  // should be trimmed correctly
        assertEquals(LabelEditViewModel.Error.NONE, viewModel.nameError.getOrAwaitValue())
        viewModel.addLabel()
        assertEquals(Label(3, "my label", false), labelsRepo.getLabelById(3))
    }

    @Test
    fun `should rename label`() = runTest {
        viewModel.start(1)
        assertLiveDataEventSent(viewModel.setLabelEvent, Label(1, "label0", false))
        viewModel.onNameChanged("edited label")
        assertEquals(LabelEditViewModel.Error.NONE, viewModel.nameError.getOrAwaitValue())
        viewModel.addLabel()
        assertEquals(Label(1, "edited label", false), labelsRepo.getLabelById(1))
    }

    @Test
    fun `should set label hidden`() = runTest {
        viewModel.start(1)
        assertLiveDataEventSent(viewModel.setLabelEvent, Label(1, "label0", false))
        viewModel.onHiddenChanged(true)
        viewModel.addLabel()
        assertEquals(Label(1, "label0", true), labelsRepo.getLabelById(1))
    }

    @Test
    fun `should not change label`() = runTest {
        viewModel.start(2)
        viewModel.addLabel()
        assertEquals(Label(2, "label1", true), labelsRepo.getLabelById(2))
    }

    @Test
    fun `should have error if name is empty`() = runTest {
        viewModel.start(Label.NO_ID)
        assertEquals(LabelEditViewModel.Error.BLANK, viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("    ")
        assertEquals(LabelEditViewModel.Error.BLANK, viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("l")
        assertEquals(LabelEditViewModel.Error.NONE, viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("")
        assertEquals(LabelEditViewModel.Error.BLANK, viewModel.nameError.getOrAwaitValue())
    }

    @Test
    fun `should have error if name exists`() = runTest {
        viewModel.start(Label.NO_ID)
        viewModel.onNameChanged("label0")
        assertEquals(LabelEditViewModel.Error.DUPLICATE, viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("label1")
        assertEquals(LabelEditViewModel.Error.DUPLICATE, viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("test")
        assertEquals(LabelEditViewModel.Error.NONE, viewModel.nameError.getOrAwaitValue())
    }

    @Test
    fun `should not have error if name is the one of edited label`() = runTest {
        viewModel.start(1)
        viewModel.onNameChanged("label0")
        assertEquals(LabelEditViewModel.Error.NONE, viewModel.nameError.getOrAwaitValue())
    }
}
