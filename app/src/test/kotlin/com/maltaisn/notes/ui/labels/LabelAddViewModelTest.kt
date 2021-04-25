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

class LabelAddViewModelTest {

    private lateinit var viewModel: LabelAddViewModel

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

        viewModel = LabelAddViewModel(labelsRepo)
    }

    @Test
    fun `should add label`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNameChanged("my label")
        assertFalse(viewModel.nameError.getOrAwaitValue())
        viewModel.addLabel()
        assertEquals(Label(3, "my label"), labelsRepo.getLabelById(3))
    }

    @Test
    fun `should have error if name is empty`() = mainCoroutineRule.runBlockingTest {
        assertTrue(viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("l")
        assertFalse(viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("")
        assertTrue(viewModel.nameError.getOrAwaitValue())
    }

    @Test
    fun `should have error if name exists`() = mainCoroutineRule.runBlockingTest {
        viewModel.onNameChanged("label0")
        assertTrue(viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("label1")
        assertTrue(viewModel.nameError.getOrAwaitValue())
        viewModel.onNameChanged("test")
        assertFalse(viewModel.nameError.getOrAwaitValue())
    }

}
