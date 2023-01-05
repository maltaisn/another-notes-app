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

package com.maltaisn.notes.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.ui.main.MainActivity
import com.maltaisn.notes.ui.notification.NotificationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AlarmReceiver : BroadcastReceiver() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @Inject
    lateinit var reminderAlarmManager: ReminderAlarmManager

    @Inject
    lateinit var notesRepository: NotesRepository

    override fun onReceive(context: Context?, intent: Intent) {
        if (context == null) return

        (context.applicationContext as App).appComponent.inject(this)

        coroutineScope.launch {
            val noteId = intent.getLongExtra(EXTRA_NOTE_ID, Note.NO_ID)
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> reminderAlarmManager.updateAllAlarms()
                ACTION_ALARM -> showNotificationForReminder(context, noteId)
                ACTION_MARK_DONE -> markReminderAsDone(context, noteId)
            }
        }
    }

    /**
     * Receiver was called for reminder alarm, show a notification with the note title and content.
     * Clicking the notification opens the app to edit/view it.
     * Two action buttons can be clicked: mark as done and postpone.
     */
    private suspend fun showNotificationForReminder(context: Context, noteId: Long) {
        val note = notesRepository.getNoteById(noteId) ?: return

        reminderAlarmManager.setNextNoteReminderAlarm(note)

        var pendingIntentBaseFlags = 0
        if (Build.VERSION.SDK_INT >= 23) {
            pendingIntentBaseFlags = pendingIntentBaseFlags or PendingIntent.FLAG_IMMUTABLE
        }

        val noteText = note.asText(includeTitle = false).ifBlank { null }
        val builder = NotificationCompat.Builder(context, App.NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setGroup(NOTIFICATION_GROUP)
            .setContentTitle(note.title.ifBlank { null })
            .setContentText(noteText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(noteText))
            .setAutoCancel(true)

        // Edit/view main action
        val notifIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.INTENT_ACTION_EDIT
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        builder.setContentIntent(PendingIntent.getActivity(context,
            noteId.toInt(), notifIntent, pendingIntentBaseFlags))

        // Add actions for non-recurring reminders
        if (note.reminder?.recurrence == null) {
            val pendingIntentFlags = pendingIntentBaseFlags or PendingIntent.FLAG_UPDATE_CURRENT

            // Mark done action
            val markDoneIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_MARK_DONE
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            builder.addAction(R.drawable.ic_check, context.getString(R.string.action_mark_as_done),
                PendingIntent.getBroadcast(context, noteId.toInt(), markDoneIntent, pendingIntentFlags))

            // Postpone action only if not recurring.
            val postponeIntent = Intent(context, NotificationActivity::class.java).apply {
                action = NotificationActivity.INTENT_ACTION_POSTPONE
                putExtra(EXTRA_NOTE_ID, noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            builder.addAction(R.drawable.ic_calendar,
                context.getString(R.string.action_postpone),
                PendingIntent.getActivity(context, noteId.toInt(), postponeIntent, pendingIntentFlags))
        }

        NotificationManagerCompat.from(context).notify(noteId.toInt(), builder.build())
    }

    private suspend fun markReminderAsDone(context: Context, noteId: Long) {
        reminderAlarmManager.markReminderAsDone(noteId)
        withContext(Dispatchers.Main) {
            NotificationManagerCompat.from(context).cancel(noteId.toInt())
        }
    }

    companion object {
        const val ACTION_ALARM = "com.maltaisn.notes.reminder.ALARM"
        const val ACTION_MARK_DONE = "com.maltaisn.notes.reminder.MARK_DONE"

        const val EXTRA_NOTE_ID = "com.maltaisn.notes.reminder.NOTE_ID"
        const val NOTIFICATION_GROUP = "com.maltaisn.notes.reminder.REMINDERS"
    }
}
