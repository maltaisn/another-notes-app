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

package com.maltaisn.notes.ui.notification

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.maltaisn.notes.App
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.observeEvent
import java.util.Calendar
import javax.inject.Inject

class NotificationActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: NotificationViewModel.Factory
    private val viewModel by navGraphViewModel(R.id.nav_graph_notification) { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        setContentView(R.layout.activity_notification)

        setupViewModelObservers()

        handleIntent(intent)
    }

    private fun setupViewModelObservers() {

        viewModel.showDateDialogEvent.observeEvent(this) { date ->
            val calendarConstraints = CalendarConstraints.Builder()
                .setStart(System.currentTimeMillis())
                .setValidator(DateValidatorPointForward.now())
                .build()

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(System.currentTimeMillis())
                .setCalendarConstraints(calendarConstraints)
                .setSelection(date)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                viewModel.setPostponeDate(selection)
            }

            datePicker.addOnNegativeButtonClickListener {
                viewModel.cancelPostpone()
            }

            datePicker.show(supportFragmentManager, datePicker.tag)
        }

        viewModel.showTimeDialogEvent.observeEvent(this) { date ->
            val isUsing24HourFormat = DateFormat.is24HourFormat(this)
            val timeFormat = if (isUsing24HourFormat) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = date

            val timePicker = MaterialTimePicker.Builder()
                .setHour(calendar[Calendar.HOUR_OF_DAY])
                .setMinute(calendar[Calendar.MINUTE])
                .setTimeFormat(timeFormat)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                viewModel.setPostponeTime(timePicker.hour, timePicker.minute)
            }

            timePicker.addOnNegativeButtonClickListener {
                viewModel.cancelPostpone()
            }

            timePicker.show(supportFragmentManager, timePicker.tag)
        }

        viewModel.clearNotificationEvent.observeEvent(this) { noteId ->
            NotificationManagerCompat.from(this).cancel(noteId.toInt())
        }

        viewModel.exitEvent.observeEvent(this) {
            finish()
        }
    }

    private fun handleIntent(intent: Intent) {
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                INTENT_ACTION_POSTPONE -> {
                    val noteId = intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID)
                    viewModel.onPostponeClicked(noteId)
                }
            }

            // Mark intent as handled or it will be handled again if activity restarts.
            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "com.maltaisn.notes.INTENT_HANDLED"

        const val INTENT_ACTION_POSTPONE = "com.maltaisn.notes.reminder.POSTPONE"
    }
}
