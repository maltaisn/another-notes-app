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

package com.maltaisn.notes.ui.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.assertNoteEquals
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.model.MockNotesRepository
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.test.assertEquals

class NotificationViewModelTest {

    private lateinit var viewModel: NotificationViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var alarmCallback: MockAlarmCallback

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        notesRepo = MockNotesRepository()
        notesRepo.addNote(testNote(id = 1, reminder = Reminder(
            dateFor("2100-01-24T08:13:00.000"), null,
            dateFor("2100-01-24T08:13:00.000"), 1, false)))
        notesRepo.addNote(testNote(id = 2))
        notesRepo.addNote(testNote(id = 3, reminder = Reminder(
            dateFor("2100-01-23T21:38:00.000"), null,
            dateFor("2100-01-23T21:38:00.000"), 1, false)))

        alarmCallback = MockAlarmCallback()

        viewModel = NotificationViewModel(SavedStateHandle(), notesRepo,
            ReminderAlarmManager(notesRepo, alarmCallback))
    }

    @Test
    fun `should change date and time for reminder`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!
        viewModel.onPostponeClicked(1)

        val date = viewModel.showDateDialogEvent.getOrAwaitValue().requireUnhandledContent()
        assertOnSameDay(date, dateFor("2100-01-24T08:13:00.000").time)
        viewModel.setPostponeDate(2100, Calendar.FEBRUARY, 2)

        // to bypass delay workaround with navigation component
        advanceTimeBy(1000)

        val time = viewModel.showTimeDialogEvent.getOrAwaitValue().requireUnhandledContent()
        val timeCal = Calendar.getInstance()
        timeCal.timeInMillis = time
        assertEquals(9, timeCal[Calendar.HOUR_OF_DAY])
        assertEquals(13, timeCal[Calendar.MINUTE])
        viewModel.setPostponeTime(23, 59)

        val postponeTime = dateFor("2100-02-02T23:59:00.000")
        assertLiveDataEventSent(viewModel.exitEvent)
        assertEquals(1, alarmCallback.alarms.size)
        assertEquals(postponeTime.time, alarmCallback.alarms[1])
        assertNoteEquals(oldNote.copy(reminder = oldNote.reminder?.postponeTo(postponeTime)),
            notesRepo.getById(1)!!)
    }

    @Test
    fun `should cancel postpone on date dialog`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!
        viewModel.onPostponeClicked(1)
        viewModel.cancelPostpone()
        assertLiveDataEventSent(viewModel.exitEvent)
        assertEquals(0, alarmCallback.alarms.size)
        assertEquals(oldNote, notesRepo.getById(1))
    }

    @Test
    fun `should cancel postpone on time dialog`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!
        viewModel.onPostponeClicked(1)
        viewModel.setPostponeDate(2021, Calendar.JANUARY, 1)
        viewModel.cancelPostpone()
        assertLiveDataEventSent(viewModel.exitEvent)
        assertEquals(0, alarmCallback.alarms.size)
        assertEquals(oldNote, notesRepo.getById(1))
    }

    @Test
    fun `should exit if note has no reminder`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(2)!!
        viewModel.onPostponeClicked(2)
        assertLiveDataEventSent(viewModel.exitEvent)
        assertEquals(0, alarmCallback.alarms.size)
        assertEquals(oldNote, notesRepo.getById(2))
    }

    @Test
    fun `should exit if note has no reminder at the end`() = mainCoroutineRule.runBlockingTest {
        viewModel.onPostponeClicked(1)
        viewModel.setPostponeDate(2100, Calendar.JANUARY, 1)

        // delete note reminder in the meantime
        val oldNote = notesRepo.getById(1)!!.copy(reminder = null)
        notesRepo.updateNote(oldNote)

        viewModel.setPostponeTime(13, 50)
        assertLiveDataEventSent(viewModel.exitEvent)
        assertEquals(0, alarmCallback.alarms.size)
        assertEquals(oldNote, notesRepo.getById(1))
    }

    @Test
    fun `should do nothing if postponed in the past`() = mainCoroutineRule.runBlockingTest {
        val oldNote = notesRepo.getById(1)!!
        viewModel.onPostponeClicked(1)
        viewModel.setPostponeDate(2000, Calendar.JANUARY, 1)
        viewModel.setPostponeTime(13, 50)
        assertLiveDataEventSent(viewModel.exitEvent)
        assertEquals(0, alarmCallback.alarms.size)
        assertEquals(oldNote, notesRepo.getById(1))
    }

    @Test
    fun `should postpone reminder bug 1`() = mainCoroutineRule.runBlockingTest {
        // bug was due to using HOUR instead of HOUR_OF_DAY
        val oldNote = notesRepo.getById(3)!!
        viewModel.onPostponeClicked(3)
        viewModel.setPostponeDate(2100, Calendar.JANUARY, 23)
        viewModel.setPostponeTime(22, 38)
        val postponeTime = dateFor("2100-01-23T22:38:00.000")
        assertEquals(oldNote.copy(reminder = oldNote.reminder?.postponeTo(postponeTime)),
            notesRepo.getById(3))
    }

    private fun assertOnSameDay(expected: Long, actual: Long) {
        val fmt = SimpleDateFormat("yyyy-MM-dd")
        assertEquals(fmt.format(expected), fmt.format(actual))
    }
}
