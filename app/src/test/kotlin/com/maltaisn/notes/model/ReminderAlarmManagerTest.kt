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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.maltaisn.notes.MainCoroutineRule
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.testNote
import com.maltaisn.notes.ui.MockAlarmCallback
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderAlarmManagerTest {

    private lateinit var alarmManager: ReminderAlarmManager

    private lateinit var notesRepo: MockNotesRepository
    private lateinit var alarmCallback: MockAlarmCallback

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        val recurFinder = RecurrenceFinder()
        notesRepo = MockNotesRepository(MockLabelsRepository())
        notesRepo.addNote(testNote(id = 1,
            reminder = Reminder.create(dateFor("2021-01-01"), null, recurFinder)))
        notesRepo.addNote(testNote(id = 2,
            reminder = Reminder.create(dateFor("2100-01-01"),
                Recurrence(Recurrence.Period.DAILY) { endCount = 3 }, recurFinder)))
        notesRepo.addNote(testNote(id = 3,
            reminder = Reminder(dateFor("2100-02-23"), null,
                dateFor("2021-02-23"), 1, true)))
        notesRepo.addNote(testNote(id = 4))
        notesRepo.addNote(testNote(id = 5,
            reminder = Reminder.create(dateFor("2100-02-01"), null, recurFinder)))
        notesRepo.addNote(testNote(id = 6,
            reminder = Reminder.create(dateFor("2000-03-01"),
                Recurrence(Recurrence.Period.YEARLY) { frequency = 100 }, recurFinder)))
        notesRepo.addNote(testNote(id = 7,
            reminder = Reminder.create(dateFor("2000-12-31T23:59:59.999"),
                Recurrence(Recurrence.Period.YEARLY), recurFinder)))

        alarmCallback = MockAlarmCallback()

        alarmManager = ReminderAlarmManager(notesRepo, alarmCallback)
    }

    @Test
    fun `should set reminder alarm`() = runTest {
        val note = notesRepo.requireNoteById(1)
        alarmManager.setNoteReminderAlarm(note)
        assertEquals(dateFor("2021-01-01").time, alarmCallback.alarms[1])
    }

    @Test
    fun `should remove reminder alarm`() = runTest {
        alarmCallback.addAlarm(4, 1000)
        val note = notesRepo.requireNoteById(4)
        alarmManager.setNoteReminderAlarm(note)
        assertNull(alarmCallback.alarms[4])
    }

    @Test
    fun `should set next alarm for non-recurring reminder (does nothing)`() = runTest {
        val note = notesRepo.requireNoteById(1)
        alarmManager.setNextNoteReminderAlarm(note)
        assertNull(alarmCallback.alarms[1])
    }

    @Test
    fun `should set next alarm for recurring reminder (same event)`() = runTest {
        alarmManager.setNextNoteReminderAlarm(notesRepo.requireNoteById(2))
        assertEquals(dateFor("2100-01-01").time, alarmCallback.alarms[2])
        alarmCallback.removeAlarm(2)
    }

    @Test
    fun `should set next alarm for recurring reminder (skipping events)`() = runTest {
        alarmManager.setNextNoteReminderAlarm(notesRepo.requireNoteById(7))
        val year = Calendar.getInstance()[Calendar.YEAR]
        assertEquals(dateFor("$year-12-31T23:59:59.999").time, alarmCallback.alarms[7])
    }

    @Test
    fun `should mark reminder as done`() = runTest {
        alarmManager.markReminderAsDone(1)
        assertTrue(notesRepo.requireNoteById(1).reminder!!.done)
    }

    @Test
    fun `should update all alarms`() = runTest {
        alarmCallback.addAlarm(1, dateFor("2021-01-01").time)
        alarmCallback.addAlarm(6, dateFor("2000-03-01").time)

        alarmManager.updateAllAlarms()

        assertNull(alarmCallback.alarms[1])
        assertNull(alarmCallback.alarms[3])
        assertNull(alarmCallback.alarms[4])
        assertEquals(dateFor("2100-01-01").time, alarmCallback.alarms[2])
        assertEquals(dateFor("2100-02-01").time, alarmCallback.alarms[5])
        assertEquals(dateFor("2100-03-01").time, alarmCallback.alarms[6])

        assertEquals(dateFor("2100-03-01"), notesRepo.requireNoteById(6).reminder!!.next)
    }

    @Test
    fun `should remove all alarms`() = runTest {
        alarmCallback.addAlarm(1, dateFor("2021-01-01").time)
        alarmCallback.addAlarm(6, dateFor("2000-03-01").time)

        alarmManager.removeAllAlarms()

        assertEquals(0, alarmCallback.alarms.size)
    }
}
