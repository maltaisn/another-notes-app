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

package com.maltaisn.notes.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.maltaisn.notes.App
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.sync.R
import com.maltaisn.recurpicker.RecurrenceFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class AlarmReceiver : BroadcastReceiver() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Inject
    lateinit var notesRepository: NotesRepository

    override fun onReceive(context: Context?, intent: Intent) {
        if (context == null) return

        (context.applicationContext as App).appComponent.inject(this)

        val recurrenceFinder = RecurrenceFinder()

        if (intent.action != null) {
            if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true)) {
                // Device booted, set all alarms again.
                coroutineScope.launch {
                    val date = Date()
                    val updatedNotes = mutableListOf<Note>()
                    for (note in notesRepository.getNotesWithReminder()) {
                        var reminder = note.reminder!!

                        if (reminder.next.before(date)) {
                            // Reminder happened while device was shutdown, skip.
                            val nextReminder = reminder.findNextReminder(recurrenceFinder)
                            if (nextReminder !== reminder) {
                                // Recurring reminder, update note.
                                updatedNotes += note.copy(reminder = nextReminder)
                                reminder = nextReminder
                            }
                        }

                        setNoteReminderAlarm(context, note)
                    }

                    notesRepository.updateNotes(updatedNotes)
                }
            }

        } else {
            coroutineScope.launch {
                val id = intent.getLongExtra(EXTRA_NOTE_ID, Note.NO_ID)
                val note = notesRepository.getById(id) ?: return@launch

                // Update note in database if reminder is recurring
                val nextReminder = note.reminder?.findNextReminder(recurrenceFinder)
                if (nextReminder != null && nextReminder !== note.reminder) {
                    notesRepository.updateNote(note.copy(reminder = nextReminder))
                    setNoteReminderAlarm(context, note)
                }

                // Show notification
                withContext(Dispatchers.Main) {
                    val notification = NotificationCompat.Builder(
                        context, App.NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_alarm)
                        .setContentTitle(note.title)
                        .setContentText(note.content)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                    NotificationManagerCompat.from(context).notify(note.id.toInt(), notification)
                }
            }
        }
    }

    private fun setNoteReminderAlarm(context: Context, note: Note) {
        val date = (note.reminder?.next ?: return).time
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE)
                as? AlarmManager ?: return
        val receiverIntent = Intent(context, AlarmReceiver::class.java)
        receiverIntent.putExtra(EXTRA_NOTE_ID, note.id)
        val alarmIntent = PendingIntent.getBroadcast(context,
            note.id.toInt(), receiverIntent, 0)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, date, alarmIntent)
    }

    companion object {
        const val EXTRA_NOTE_ID = "noteId"
    }

}
