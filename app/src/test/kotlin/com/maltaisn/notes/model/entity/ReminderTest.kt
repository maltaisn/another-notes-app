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

package com.maltaisn.notes.model.entity

import com.maltaisn.notesshared.dateFor
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import org.junit.Test
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReminderTest {

    private val finder = RecurrenceFinder().apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    @Test
    fun `should create reminder with no recurrence`() {
        val reminder = Reminder.create(dateFor("2020-07-29"),
            Recurrence.DOES_NOT_REPEAT, finder)
        assertNull(reminder.recurrence)
    }

    @Test
    fun `should create reminder with next date set (same as start date)`() {
        val reminder = Reminder.create(dateFor("2020-07-29"),
            Recurrence(Recurrence.Period.DAILY), finder)
        assertEquals(dateFor("2020-07-29"), reminder.next)
        assertFalse(reminder.done)
    }

    @Test
    fun `should create reminder with next date set (different than start date)`() {
        val reminder = Reminder.create(dateFor("2020-07-29"),
            Recurrence(Recurrence.Period.WEEKLY) {
                setDaysOfWeek(Recurrence.THURSDAY)
            }, finder)
        assertEquals(dateFor("2020-07-30"), reminder.next)
        assertFalse(reminder.done)
    }

    @Test
    fun `should fail to create recurring reminder with count less than 1`() {
        assertFailsWith<IllegalArgumentException> {
            Reminder(dateFor("2020-07-29"), Recurrence(Recurrence.Period.DAILY),
                dateFor("2020-07-29"), 0, false)
        }
    }

    @Test
    fun `should fail to create non-recurring reminder with count not equal to 1`() {
        assertFailsWith<IllegalArgumentException> {
            Reminder(dateFor("2020-07-29"), null,
                dateFor("2020-07-29"), 12, false)
        }
    }

    @Test
    fun `should find next reminder`() {
        val recurrence = Recurrence(Recurrence.Period.DAILY)
        val reminder = Reminder.create(dateFor("2020-07-29"), recurrence, finder)
        assertEquals(Reminder(dateFor("2020-07-29"), recurrence,
            dateFor("2020-07-30"), 2, false),
            reminder.findNextReminder(finder))
        assertFalse(reminder.done)
    }

    @Test
    fun `should find next reminder (recurrence is done)`() {
        val recurrence = Recurrence.DOES_NOT_REPEAT
        val reminder = Reminder.create(dateFor("2020-07-29"), recurrence, finder)
        assertSame(reminder, reminder.findNextReminder(finder))
        assertFalse(reminder.done)
    }

    @Test
    fun `should postpone reminder`() {
        val reminder = Reminder.create(dateFor("2020-07-29"), null, finder)
        assertEquals(Reminder(dateFor("2020-07-29"), null,
            dateFor("2020-07-30"), 1, false),
            reminder.postponeTo(dateFor("2020-07-30")))
    }

    @Test
    fun `should fail to postpone recurring reminder`() {
        val recurrence = Recurrence(Recurrence.Period.DAILY)
        val reminder = Reminder.create(dateFor("2020-07-29"), recurrence, finder)
        assertFailsWith<IllegalArgumentException> {
            reminder.postponeTo(dateFor("2020-07-30"))
        }
    }

    @Test
    fun `should fail to postpone reminder marked as done`() {
        val reminder = Reminder.create(dateFor("2020-07-29"), null, finder)
            .markAsDone()
        assertFailsWith<IllegalArgumentException> {
            reminder.postponeTo(dateFor("2020-07-30"))
        }
    }

    @Test
    fun `should mark reminder as done`() {
        val reminder = Reminder.create(dateFor("2020-07-29"), null, finder)
        assertFalse(reminder.done)
        val doneReminder = reminder.markAsDone()
        assertTrue(doneReminder.done)
    }

    @Test
    fun `should fail to create recurring reminder with no events`() {
        val recurrence = Recurrence(Recurrence.Period.DAILY) {
            endDate = dateFor("2020-01-01").time
        }
        assertFailsWith<Reminder.InvalidReminderException> {
            Reminder.create(dateFor("2020-07-29"), recurrence, finder)
        }
    }
}
