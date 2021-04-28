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

package com.maltaisn.notes.model

import com.maltaisn.notes.model.entity.Note
import com.maltaisn.recurpicker.RecurrenceFinder
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

class ReminderAlarmManager @Inject constructor(
    private val notesRepository: NotesRepository,
    private val alarmCallback: ReminderAlarmCallback
) {

    private val recurrenceFinder = RecurrenceFinder()

    suspend fun updateAllAlarms() {
        val now = Date()
        val notesToUpdate = mutableListOf<Note>()
        val notes = notesRepository.getNotesWithReminder().first().asSequence().map { it.note }
        for (note in notes) {
            var reminder = note.reminder!!

            // For recurring reminders, skip all past events and
            // find first event that hasn't happened yet, or last event.
            while (reminder.next.before(now)) {
                val nextReminder = reminder.findNextReminder(recurrenceFinder)
                if (nextReminder !== reminder) {
                    reminder = nextReminder
                } else {
                    // Recurrence done, or not recurring.
                    // Reminder will appear as overdue.
                    break
                }
            }
            if (reminder !== note.reminder) {
                // Reminder changed, update note in database.
                notesToUpdate += note.copy(reminder = reminder)
            }

            if (reminder.next.after(now)) {
                alarmCallback.addAlarm(note.id, reminder.next.time)
            } else {
                alarmCallback.removeAlarm(note.id)
            }
        }
        notesRepository.updateNotes(notesToUpdate)
    }

    fun setNoteReminderAlarm(note: Note) {
        val reminder = note.reminder
        if (reminder != null) {
            alarmCallback.addAlarm(note.id, reminder.next.time)
        } else {
            alarmCallback.removeAlarm(note.id)
        }
    }

    suspend fun setNextNoteReminderAlarm(note: Note) {
        // Update note in database if reminder is recurring
        val nextReminder = note.reminder?.findNextReminder(recurrenceFinder)
        if (nextReminder != null && nextReminder !== note.reminder) {
            notesRepository.updateNote(note.copy(reminder = nextReminder))
            alarmCallback.addAlarm(note.id, nextReminder.next.time)
        }
    }

    suspend fun markReminderAsDone(noteId: Long) {
        val note = notesRepository.getNoteById(noteId) ?: return
        notesRepository.updateNote(note.copy(reminder = note.reminder?.markAsDone()))
    }

    fun removeAlarm(noteId: Long) {
        alarmCallback.removeAlarm(noteId)
    }

}

interface ReminderAlarmCallback {
    fun addAlarm(noteId: Long, time: Long)
    fun removeAlarm(noteId: Long)
}