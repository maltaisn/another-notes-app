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

package com.maltaisn.notes.ui.reminder

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.navArgs
import com.maltaisn.notes.App
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.navGraphViewModel
import java.util.Calendar
import javax.inject.Inject

class ReminderTimeDialog : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: ReminderViewModel.Factory
    val viewModel by navGraphViewModel(R.id.nav_graph_reminder) { viewModelFactory.create(it) }

    private val args: ReminderTimeDialogArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = requireContext()

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = args.date

        return TimePickerDialog(context, { _, hour, minute ->
            viewModel.changeTime(hour, minute)
        }, calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE], DateFormat.is24HourFormat(context))
    }
}
