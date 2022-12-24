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

package com.maltaisn.notes.utils

import android.content.res.Resources
import com.maltaisn.notes.dateFor
import com.maltaisn.notes.sync.R
import com.nhaarman.mockitokotlin2.anyVararg
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import java.text.DateFormat
import java.util.Locale
import kotlin.test.assertEquals

class RelativeDateFormatterTest {

    private lateinit var formatter: RelativeDateFormatter

    @Before
    fun before() {
        Locale.setDefault(Locale.CANADA)
        val strings = mapOf(
            R.string.date_rel_today to "today, %s",
            R.string.date_rel_tomorrow to "tomorrow, %s",
            R.string.date_rel_yesterday to "yesterday, %s",
            R.string.date_rel_absolute to "%s, %s",
            R.plurals.date_rel_days_past to "%d days ago, %s",
            R.plurals.date_rel_days_future to "in %d days, %s",
        )
        val resources: Resources = mock {
            on { getString(anyInt(), anyVararg()) } doAnswer {
                strings[it.arguments[0]]?.format(*it.arguments.copyOfRange(1, it.arguments.size))
            }
            on { getQuantityString(anyInt(), anyInt(), anyVararg()) } doAnswer {
                strings[it.arguments[0]]?.format(*it.arguments.copyOfRange(2, it.arguments.size))
            }
        }
        formatter = RelativeDateFormatter(resources) { date ->
            DateFormat.getDateInstance(DateFormat.SHORT, Locale.CANADA).format(date)
        }
    }

    @Test
    fun `should return today date time`() {
        assertEquals("today, 12:34 p.m.", formatter.format(
            dateFor("2020-01-01T12:34:56.000").time,
            dateFor("2020-01-01T10:00:00.000").time,
            6
        ))
    }

    @Test
    fun `should return tomorrow date time`() {
        assertEquals("tomorrow, 11:00 a.m.", formatter.format(
            dateFor("2020-01-02T11:00:00.000").time,
            dateFor("2020-01-01T10:00:00.000").time,
            6
        ))
    }

    @Test
    fun `should return yesterday date time`() {
        assertEquals("yesterday, 11:59 p.m.", formatter.format(
            dateFor("2019-12-31T23:59:59.999").time,
            dateFor("2020-01-01T00:00:00.000").time,
            6
        ))
    }

    @Test
    fun `should return relative future date time`() {
        assertEquals("in 6 days, 12:00 a.m.", formatter.format(
            dateFor("2020-01-07T00:00:00.000").time,
            dateFor("2020-01-01T00:00:00.000").time,
            6
        ))
    }

    @Test
    fun `should return relative past date time`() {
        assertEquals("6 days ago, 12:00 a.m.", formatter.format(
            dateFor("2020-01-01T00:00:00.000").time,
            dateFor("2020-01-07T00:00:00.000").time,
            6
        ))
    }

    @Test
    fun `should return absolute date time (future)`() {
        assertEquals("2020-01-05, 12:00 a.m.", formatter.format(
            dateFor("2020-01-05T00:00:00.000").time,
            dateFor("2020-01-01T00:00:00.000").time,
            3
        ))
    }

    @Test
    fun `should return absolute date time (past)`() {
        assertEquals("2019-12-27, 12:00 a.m.", formatter.format(
            dateFor("2019-12-27T00:00:00.000").time,
            dateFor("2020-01-01T00:00:00.000").time,
            3
        ))
    }
}
