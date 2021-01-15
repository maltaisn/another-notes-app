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

package com.maltaisn.notes.ui.reminder

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.notes.App
import com.maltaisn.notes.contains
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.setMaxWidth
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.DialogReminderBinding
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrencePickerSettings
import com.maltaisn.recurpicker.format.RecurrenceFormatter
import com.maltaisn.recurpicker.list.RecurrenceListCallback
import com.maltaisn.recurpicker.list.RecurrenceListDialog
import com.maltaisn.recurpicker.picker.RecurrencePickerCallback
import com.maltaisn.recurpicker.picker.RecurrencePickerDialog
import java.text.DateFormat
import javax.inject.Inject
import javax.inject.Provider

class ReminderDialog : DialogFragment(), RecurrenceListCallback, RecurrencePickerCallback {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph) { sharedViewModelProvider.get() }

    @Inject
    lateinit var viewModelFactory: ReminderViewModel.Factory
    private val viewModel by navGraphViewModel(R.id.nav_graph_reminder) { viewModelFactory.create(it) }

    private val args: ReminderDialogArgs by navArgs()

    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val recurrenceFormat = RecurrenceFormatter(dateFormat)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val binding = DialogReminderBinding.inflate(LayoutInflater.from(context), null, false)

        binding.dateForegroundView.setOnClickListener {
            viewModel.onDateClicked()
        }
        binding.timeForegroundView.setOnClickListener {
            viewModel.onTimeClicked()
        }
        binding.recurrenceForegroundView.setOnClickListener {
            viewModel.onRecurrenceClicked()
        }

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setTitle(R.string.action_reminder_add)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.setMaxWidth(context.resources.getDimensionPixelSize(
                R.dimen.reminder_dialog_max_width), binding.root)
            onDialogShown(dialog)
        }

        setupViewModelObservers(binding)

        viewModel.start(args.noteIds.toList())

        return dialog
    }

    private fun setupViewModelObservers(binding: DialogReminderBinding) {
        // Using `this` as lifecycle owner, cannot show dialog twice with same instance to avoid double observation.
        check(!viewModel.details.hasObservers()) { "Dialog was shown twice with same instance." }

        viewModel.details.observe(this, Observer { details ->
            binding.dateInput.setText(dateFormat.format(details.date))
            binding.timeInput.setText(timeFormat.format(details.date))
            binding.recurrenceTxv.text = recurrenceFormat.format(requireContext(),
                details.recurrence, details.date)
        })

        viewModel.invalidTime.observe(this) { invalid ->
            binding.invalidTimeTxv.isVisible = invalid
        }

        viewModel.showDateDialogEvent.observeEvent(this) { date ->
            findNavController().navigateSafe(ReminderDialogDirections.actionReminderDate(date))
        }

        viewModel.showTimeDialogEvent.observeEvent(this) { date ->
            findNavController().navigateSafe(ReminderDialogDirections.actionReminderTime(date))
        }

        viewModel.showRecurrenceListDialogEvent.observeEvent(this) { details ->
            if (RECURRENCE_LIST_DIALOG_TAG !in childFragmentManager) {
                RecurrenceListDialog.newInstance(RecurrencePickerSettings()).apply {
                    startDate = details.date
                    selectedRecurrence = details.recurrence
                }.show(childFragmentManager, RECURRENCE_LIST_DIALOG_TAG)
            }
        }

        viewModel.showRecurrencePickerDialogEvent.observeEvent(this) { details ->
            if (RECURRENCE_PICKER_DIALOG_TAG !in childFragmentManager) {
                RecurrencePickerDialog.newInstance(RecurrencePickerSettings()).apply {
                    startDate = details.date
                    selectedRecurrence = details.recurrence
                }.show(childFragmentManager, RECURRENCE_PICKER_DIALOG_TAG)
            }
        }

        viewModel.reminderChangeEvent.observeEvent(this) { change ->
            sharedViewModel.onReminderChange(change.reminder)
            updateAlarmsForChange(change)
        }

        viewModel.dismissEvent.observeEvent(this) {
            dismiss()
        }
    }

    private fun onDialogShown(dialog: AlertDialog) {
        val okBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        okBtn.setOnClickListener {
            viewModel.createReminder()
        }
        viewModel.invalidTime.observe(this) { invalid ->
            okBtn.isEnabled = !invalid
        }
        val deleteBtn = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
        deleteBtn.setText(R.string.action_delete)
        deleteBtn.setOnClickListener {
            viewModel.deleteReminder()
        }
        viewModel.isEditingReminder.observe(this) { editing ->
            dialog.setTitle(if (editing) R.string.action_reminder_edit else R.string.action_reminder_add)
            deleteBtn.isVisible = editing
        }
    }

    private fun updateAlarmsForChange(change: ReminderViewModel.ReminderChange) {
        val context = requireContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE)
                as? AlarmManager ?: return
        for (id in change.noteIds) {
            // Make alarm intent
            val receiverIntent = Intent(context, AlarmReceiver::class.java)
            receiverIntent.putExtra(AlarmReceiver.EXTRA_NOTE_ID, id)
            val alarmIntent = PendingIntent.getBroadcast(context,
                id.toInt(), receiverIntent, 0)

            if (change.reminder != null) {
                // Set new alarm
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                    change.reminder.next.time, alarmIntent)
            } else {
                // Remove previous alarm
                alarmIntent.cancel()
            }
        }
    }

    override fun onRecurrenceCustomClicked() {
        viewModel.onRecurrenceCustomClicked()
    }

    override fun onRecurrencePresetSelected(recurrence: Recurrence) {
        viewModel.changeRecurrence(recurrence)
    }

    override fun onRecurrenceCreated(recurrence: Recurrence) {
        viewModel.changeRecurrence(recurrence)
    }

    companion object {
        private const val RECURRENCE_LIST_DIALOG_TAG = "recurrence-list-dialog"
        private const val RECURRENCE_PICKER_DIALOG_TAG = "recurrence-picker-dialog"
    }
}
