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

package com.maltaisn.notes.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notesshared.MainCoroutineRule
import com.maltaisn.notesshared.model.MockLabelsRepository
import com.maltaisn.notesshared.model.MockNotesRepository
import com.maltaisn.notesshared.testNote
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals

class SharedViewModelTest {

    private lateinit var viewModel: SharedViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var alarmCallback: MockAlarmCallback

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        notesRepo = MockNotesRepository(MockLabelsRepository())
        notesRepo.addNote(testNote(id = 1, status = NoteStatus.ACTIVE))
        notesRepo.addNote(testNote(id = 2, status = NoteStatus.ARCHIVED,
            pinned = PinnedStatus.CANT_PIN))
        notesRepo.addNote(testNote(id = 3, status = NoteStatus.ACTIVE,
            reminder = Reminder(Date(10), null, Date(10), 1, false)))

        alarmCallback = MockAlarmCallback()

        viewModel = SharedViewModel(notesRepo, ReminderAlarmManager(notesRepo, alarmCallback))
    }

    @Test
    fun `should do and undo status change`() = runTest {
        val note1 = notesRepo.requireNoteById(1)
        val note2 = notesRepo.requireNoteById(2)
        notesRepo.updateNote(note1.copy(status = NoteStatus.ARCHIVED,
            pinned = PinnedStatus.CANT_PIN))
        val statusChange = StatusChange(listOf(note1, note2),
            NoteStatus.ACTIVE, NoteStatus.ARCHIVED)
        viewModel.onStatusChange(statusChange)
        assertLiveDataEventSent(viewModel.statusChangeEvent, statusChange)

        viewModel.undoStatusChange()
        assertEquals(note2, notesRepo.getNoteById(2))
    }

    @Test
    fun `should undo status change and set reminder alarm back`() = runTest {
        val note = notesRepo.requireNoteById(3)
        alarmCallback.addAlarm(3, 10)
        viewModel.onStatusChange(StatusChange(listOf(note),
            NoteStatus.ACTIVE, NoteStatus.DELETED))
        alarmCallback.removeAlarm(3)  // usually done by NoteViewModel or EditViewModel
        viewModel.undoStatusChange()
        assertEquals(10, alarmCallback.alarms[3])
    }
}
