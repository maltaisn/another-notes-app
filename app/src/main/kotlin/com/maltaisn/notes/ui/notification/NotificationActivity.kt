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

package com.maltaisn.notes.ui.notification

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.findNavController
import com.maltaisn.notes.App
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.observeEvent
import javax.inject.Inject

class NotificationActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: NotificationViewModel.Factory
    private val viewModel by navGraphViewModel(R.id.nav_graph_notification) { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        setContentView(R.layout.activity_notification)

        val navController = findNavController(R.id.nav_host_fragment)

        // Observers
        viewModel.showDateDialogEvent.observeEvent(this) { date ->
            navController.navigateSafe(NotificationFragmentDirections.actionReminderPostponeDate(date))
        }
        viewModel.showTimeDialogEvent.observeEvent(this) { date ->
            navController.navigateSafe(NotificationFragmentDirections.actionReminderPostponeTime(date))
        }
        viewModel.exitEvent.observeEvent(this) {
            finish()
        }

        // Use intent
        val intent = intent
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                INTENT_ACTION_POSTPONE -> {
                    val noteId = intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID)
                    NotificationManagerCompat.from(this).cancel(noteId.toInt())
                    viewModel.onPostponeClicked(noteId)
                }
            }

            // Mark intent as handled or it will be handled again if activity restarts.
            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "intent_handled"

        const val INTENT_ACTION_POSTPONE = "com.maltaisn.notes.reminder.postpone"
    }

}