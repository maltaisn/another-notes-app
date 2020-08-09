/*
 * Copyright 2020 Nicolas Maltais
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

import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import org.junit.Test
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReminderTest {

    private val finder = RecurrenceFinder().apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    @Test
    fun `should create reminder with next date set (same as start date)`() {
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"),
            Recurrence(Recurrence.Period.DAILY), finder)
        assertEquals(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), reminder.next)
        assertFalse(reminder.done)
    }

    @Test
    fun `should create reminder with next date set (different than start date)`() {
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"),
            Recurrence(Recurrence.Period.WEEKLY) {
                setDaysOfWeek(Recurrence.THURSDAY)
            }, finder)
        assertEquals(DateTimeConverter.toDate("2020-07-30T00:00:00.000Z"), reminder.next)
        assertFalse(reminder.done)
    }

    @Test
    fun `should fail to create recurring reminder with count less than 1`() {
        assertFailsWith<IllegalArgumentException> {
            Reminder(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), Recurrence(Recurrence.Period.DAILY),
                DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), 0, false)
        }
    }

    @Test
    fun `should fail to create non-recurring reminder with count not equal to 1`() {
        assertFailsWith<IllegalArgumentException> {
            Reminder(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), null,
                DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), 12, false)
        }
    }

    @Test
    fun `should find next reminder`() {
        val recurrence = Recurrence(Recurrence.Period.DAILY)
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), recurrence, finder)
        assertEquals(Reminder(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), recurrence,
            DateTimeConverter.toDate("2020-07-30T00:00:00.000Z"), 2, false),
            reminder.findNextReminder(finder))
        assertFalse(reminder.done)
    }

    @Test
    fun `should find next reminder (recurrence is done)`() {
        val recurrence = Recurrence.DOES_NOT_REPEAT
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), recurrence, finder)
        assertSame(reminder, reminder.findNextReminder(finder))
        assertFalse(reminder.done)
    }

    @Test
    fun `should postpone reminder`() {
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), null, finder)
        assertEquals(Reminder(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), null,
            DateTimeConverter.toDate("2020-07-30T00:00:00.000Z"), 1, false),
            reminder.postponeTo(DateTimeConverter.toDate("2020-07-30T00:00:00.000Z")))
    }

    @Test
    fun `should fail to postpone recurring reminder`() {
        val recurrence = Recurrence(Recurrence.Period.DAILY)
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), recurrence, finder)
        assertFailsWith<IllegalArgumentException> {
            reminder.postponeTo(DateTimeConverter.toDate("2020-07-30T00:00:00.000Z"))
        }
    }

    @Test
    fun `should fail to postpone reminder marked as done`() {
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), null, finder)
            .markAsDone()
        assertFailsWith<IllegalArgumentException> {
            reminder.postponeTo(DateTimeConverter.toDate("2020-07-30T00:00:00.000Z"))
        }
    }

    @Test
    fun `should mark reminder as done`() {
        val reminder = Reminder.create(DateTimeConverter.toDate("2020-07-29T00:00:00.000Z"), null, finder)
        assertFalse(reminder.done)
        val doneReminder = reminder.markAsDone()
        assertTrue(doneReminder.done)
    }
}
