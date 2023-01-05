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

package com.maltaisn.notes.utils

import android.content.res.Resources
import android.icu.text.RelativeDateTimeFormatter
import android.text.format.DateUtils
import com.maltaisn.notes.R
import com.maltaisn.notes.setToStartOfDay
import java.text.DateFormat
import java.util.Calendar

/**
 * [RelativeDateTimeFormatter] is somewhat buggy and couldn't do for what I wanted.
 * For example it seems stuck on AM/PM time format, even when on device with 24 hour time setting.
 * This is a relatively simple implementation of equivalent functionality.
 * The only downside is a few additional strings to translate.
 *
 * This class is not thread-safe.
 *
 * @property absoluteDateFormatter Date formatter used for absolute dates.
 */
class RelativeDateFormatter(
    private val resources: Resources,
    private val absoluteDateFormatter: (date: Long) -> String
) {

    private val calendar = Calendar.getInstance()

    fun format(date: Long, now: Long, maxRelativeDays: Int): String {
        calendar.timeInMillis = date
        calendar.setToStartOfDay()
        val dateStart = calendar.timeInMillis

        calendar.timeInMillis = now
        calendar.setToStartOfDay()
        val nowStart = calendar.timeInMillis

        val days = ((dateStart - nowStart) / DateUtils.DAY_IN_MILLIS).toInt()
        val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        return when (days) {
            0 -> resources.getString(R.string.date_rel_today, timeStr)
            1 -> resources.getString(R.string.date_rel_tomorrow, timeStr)
            -1 -> resources.getString(R.string.date_rel_yesterday, timeStr)
            in 2..maxRelativeDays -> resources.getQuantityString(
                R.plurals.date_rel_days_future, days, days, timeStr)
            in -2 downTo -maxRelativeDays -> resources.getQuantityString(
                R.plurals.date_rel_days_past, -days, -days, timeStr)
            else -> resources.getString(R.string.date_rel_absolute,
                absoluteDateFormatter(date), timeStr)
        }
    }
}
