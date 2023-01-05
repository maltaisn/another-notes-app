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

package com.maltaisn.notes.ui.reminder

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.notes.ui.assertLiveDataEventSent
import com.maltaisn.notes.ui.getOrAwaitValue
import com.maltaisn.notesshared.MainCoroutineRule
import com.maltaisn.notesshared.dateFor
import com.maltaisn.notesshared.model.MockLabelsRepository
import com.maltaisn.notesshared.model.MockNotesRepository
import com.maltaisn.notesshared.testNote
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderViewModelTest {

    private lateinit var viewModel: ReminderViewModel

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var alarmCallback: MockAlarmCallback

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        notesRepo = MockNotesRepository(MockLabelsRepository())
        notesRepo.addNote(testNote(id = 1, reminder = Reminder(
            dateFor("2020-08-15T00:00:00.000"), Recurrence(Recurrence.Period.DAILY),
            dateFor("2020-08-15T00:00:00.000"), 1, false)))
        notesRepo.addNote(testNote(id = 2, reminder = Reminder(
            dateFor("2020-08-15T00:00:00.000"), null,
            dateFor("2020-08-15T00:00:00.000"), 1, false)))
        notesRepo.addNote(testNote(id = 3, added = Date(10), modified = Date(10)))

        alarmCallback = MockAlarmCallback()
        alarmCallback.alarms[1] = dateFor("2020-08-15T00:00:00.000").time
        alarmCallback.alarms[2] = dateFor("2020-08-15T00:00:00.000").time

        viewModel = ReminderViewModel(SavedStateHandle(), notesRepo,
            ReminderAlarmManager(notesRepo, alarmCallback))
    }

    @Test
    fun `should fail to start with no note ids`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            viewModel.start(emptyList())
        }
    }

    @Test
    fun `should start with single note without reminder`() = runTest {
        viewModel.start(listOf(3))
        assertFalse(viewModel.isEditingReminder.getOrAwaitValue())
        assertFalse(viewModel.isDeleteBtnVisible.getOrAwaitValue())
    }

    @Test
    fun `should start with multiple notes`() = runTest {
        viewModel.start(listOf(1, 3))
        assertFalse(viewModel.isEditingReminder.getOrAwaitValue())
        assertTrue(viewModel.isDeleteBtnVisible.getOrAwaitValue())
    }

    @Test
    fun `should edit single note with reminder`() = runTest {
        viewModel.start(listOf(1))
        assertTrue(viewModel.isEditingReminder.getOrAwaitValue())
        assertTrue(viewModel.isDeleteBtnVisible.getOrAwaitValue())

        // Values set should be the same as the reminder used by the note.
        val reminder = notesRepo.requireNoteById(1).reminder!!
        val details = viewModel.details.getOrAwaitValue()
        assertEquals(ReminderViewModel.ReminderDetails(reminder.start.time, reminder.recurrence!!),
            details)
    }

    @Test
    fun `should use no recurrence by default when creating reminder`() = runTest {
        viewModel.start(listOf(3))
        assertEquals(Recurrence.DOES_NOT_REPEAT, viewModel.details.getOrAwaitValue().recurrence)
    }

    @Test
    fun `should show date dialog with correct date`() = runTest {
        viewModel.start(listOf(1))
        viewModel.onDateClicked()

        val reminder = notesRepo.requireNoteById(1).reminder!!
        val date = viewModel.showDateDialogEvent.getOrAwaitValue().requireUnhandledContent()
        assertOnSameDay(reminder.start.time, date)
    }

    @Test
    fun `should show time dialog`() = runTest {
        val reminder = notesRepo.requireNoteById(1).reminder!!
        viewModel.start(listOf(1))
        viewModel.onTimeClicked()
        assertLiveDataEventSent(viewModel.showTimeDialogEvent, reminder.start.time)
    }

    @Test
    fun `should show recurrence list dialog`() = runTest {
        viewModel.start(listOf(1))
        viewModel.onRecurrenceClicked()
        val reminder = notesRepo.requireNoteById(1).reminder!!
        val details =
            viewModel.showRecurrenceListDialogEvent.getOrAwaitValue().requireUnhandledContent()
        assertOnSameDay(reminder.start.time, details.date)
        assertEquals(reminder.recurrence, details.recurrence)
    }

    @Test
    fun `should show recurrence picker dialog`() = runTest {
        viewModel.start(listOf(1))
        viewModel.onRecurrenceCustomClicked()
        val reminder = notesRepo.requireNoteById(1).reminder!!
        val details = viewModel.details.getOrAwaitValue()
        assertOnSameDay(reminder.start.time, details.date)
        assertEquals(reminder.recurrence, details.recurrence)
    }

    @Test
    fun `should update reminder details when changing date`() = runTest {
        val date = GregorianCalendar(2020, Calendar.JANUARY, 1).timeInMillis
        viewModel.start(listOf(2))
        viewModel.changeDate(date)
        val details = viewModel.details.getOrAwaitValue()
        assertOnSameDay(date, details.date)
    }

    @Test
    fun `should update reminder details when changing time`() = runTest {
        viewModel.start(listOf(2))
        viewModel.changeTime(3, 14)
        val details = viewModel.details.getOrAwaitValue()
        val calendar = Calendar.getInstance().apply { timeInMillis = details.date }
        assertEquals(3, calendar[Calendar.HOUR_OF_DAY])
        assertEquals(14, calendar[Calendar.MINUTE])
    }

    @Test
    fun `should update reminder details when changing recurrence`() = runTest {
        viewModel.start(listOf(2))
        viewModel.changeRecurrence(Recurrence(Recurrence.Period.DAILY))
        val details = viewModel.details.getOrAwaitValue()
        assertEquals(Recurrence(Recurrence.Period.DAILY), details.recurrence)
    }

    @Test
    fun `should be invalid time if start date time is before now (yesterday)`() = runTest {
        viewModel.start(listOf(3))

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, -1)
        viewModel.changeDate(calendar.timeInMillis)
        assertTrue(viewModel.invalidTime.getOrAwaitValue())
        viewModel.changeTime(23, 59)
        assertTrue(viewModel.invalidTime.getOrAwaitValue())
    }

    @Test
    fun `should be invalid time if start date time is before now (today)`() = runTest {
        viewModel.start(listOf(3))

        val calendar = Calendar.getInstance()
        viewModel.changeDate(calendar.timeInMillis)
        viewModel.changeTime(0, 0)
        assertTrue(viewModel.invalidTime.getOrAwaitValue())
    }

    @Test
    fun `should be valid time if start date time is after now (today)`() = runTest {
        viewModel.start(listOf(3))
        val calendar = Calendar.getInstance()
        viewModel.changeDate(calendar.timeInMillis)
        viewModel.changeTime(23, 59)
        assertFalse(viewModel.invalidTime.getOrAwaitValue())
    }

    @Test
    fun `should be valid time if start date time is after now (tomorrow)`() = runTest {
        viewModel.start(listOf(3))
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, 1)
        viewModel.changeDate(calendar.timeInMillis)
        assertFalse(viewModel.invalidTime.getOrAwaitValue())
        viewModel.changeTime(0, 0)
        assertFalse(viewModel.invalidTime.getOrAwaitValue())
    }

    @Test
    fun `should be valid time by default`() = runTest {
        viewModel.start(listOf(3))
        assertFalse(viewModel.invalidTime.getOrAwaitValue())
    }

    @Test
    fun `should remove end date if start date is set after`() = runTest {
        viewModel.start(listOf(2))
        viewModel.changeRecurrence(Recurrence(Recurrence.Period.DAILY) {
            endDate = dateFor("2020-08-16").time
        })
        val date = GregorianCalendar(2021, Calendar.JANUARY, 1).timeInMillis
        viewModel.changeDate(date)
        val details = viewModel.details.getOrAwaitValue()
        assertEquals(Recurrence(Recurrence.Period.DAILY), details.recurrence)
    }

    @Test
    fun `should not remove end date if start date is set on same day`() = runTest {
        viewModel.start(listOf(2))
        val recurrence = Recurrence(Recurrence.Period.DAILY) {
            endDate = dateFor("2020-08-16").time
        }
        viewModel.changeRecurrence(recurrence)
        val date = GregorianCalendar(2020, Calendar.AUGUST, 16).timeInMillis
        viewModel.changeDate(date)
        val details = viewModel.details.getOrAwaitValue()
        assertEquals(recurrence, details.recurrence)
    }

    @Test
    fun `should change monthly recurrence on last day if start date not on last day`() = runTest {
        viewModel.start(listOf(2))
        val date1 = GregorianCalendar(2020, Calendar.JANUARY, 31).timeInMillis
        viewModel.changeDate(date1)
        viewModel.changeRecurrence(Recurrence(Recurrence.Period.MONTHLY) {
            dayInMonth = -1
        })
        val date2 = GregorianCalendar(2020, Calendar.JANUARY, 15).timeInMillis
        viewModel.changeDate(date2)
        val details = viewModel.details.getOrAwaitValue()
        assertEquals(Recurrence(Recurrence.Period.MONTHLY), details.recurrence)
    }

    @Test
    fun `should create reminder with changed fields`() = runTest {
        viewModel.start(listOf(3))
        val date = GregorianCalendar(9999, Calendar.JANUARY, 1).timeInMillis
        viewModel.changeDate(date)
        viewModel.changeTime(3, 14)
        viewModel.changeRecurrence(Recurrence(Recurrence.Period.WEEKLY))
        viewModel.createReminder()

        val reminder = Reminder.create(dateFor("9999-01-01T03:14:00.000"),
            Recurrence(Recurrence.Period.WEEKLY), RecurrenceFinder())
        val note = notesRepo.requireNoteById(3)
        assertLiveDataEventSent(viewModel.reminderChangeEvent, reminder)
        assertLiveDataEventSent(viewModel.dismissEvent)
        assertEquals(note.reminder, reminder)
//        assertNotEquals(note.lastModifiedDate, Date(10))
        assertEquals(dateFor("9999-01-01T03:14:00.000").time, alarmCallback.alarms[3])
    }

    @Test
    fun `should create non-recurring reminder`() = runTest {
        viewModel.start(listOf(3))
        val date = GregorianCalendar(9999, Calendar.JANUARY, 1).timeInMillis
        viewModel.changeDate(date)
        viewModel.changeTime(3, 14)
        viewModel.createReminder()

        val reminder = Reminder.create(dateFor("9999-01-01T03:14:00.000"), null, RecurrenceFinder())
        assertEquals(notesRepo.requireNoteById(3).reminder, reminder)
        assertLiveDataEventSent(viewModel.reminderChangeEvent, reminder)
        assertEquals(dateFor("9999-01-01T03:14:00.000").time, alarmCallback.alarms[3])
    }

    @Test
    fun `should not create reminder with no events`() = runTest {
        viewModel.start(listOf(1))
        val date = GregorianCalendar(2100, Calendar.JANUARY, 1).timeInMillis
        viewModel.changeDate(date) // Friday
        viewModel.changeRecurrence(Recurrence(Recurrence.Period.WEEKLY) {
            setDaysOfWeek(Recurrence.THURSDAY)
            endDate = dateFor("2100-01-01").time
        })
        viewModel.createReminder()

        assertLiveDataEventSent(viewModel.reminderChangeEvent, null)
        assertNull(notesRepo.requireNoteById(1).reminder)
        assertNull(alarmCallback.alarms[1])
    }

    @Test
    fun `should delete reminder`() = runTest {
        viewModel.start(listOf(1))
        viewModel.deleteReminder()
        assertNull(notesRepo.requireNoteById(1).reminder)
        assertLiveDataEventSent(viewModel.reminderChangeEvent, null)
        assertLiveDataEventSent(viewModel.dismissEvent)
        assertNull(alarmCallback.alarms[1])
    }

    private fun assertOnSameDay(expected: Long, actual: Long) {
        val fmt = SimpleDateFormat("yyyy-MM-dd")
        assertEquals(fmt.format(expected), fmt.format(actual))
    }

    private fun ReminderViewModel.changeDate(newDate: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = newDate
        this.changeDate(calendar[Calendar.YEAR], calendar[Calendar.MONTH], calendar[Calendar.DAY_OF_MONTH])
    }
}
