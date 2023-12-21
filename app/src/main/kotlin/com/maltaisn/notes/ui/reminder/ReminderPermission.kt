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

package com.maltaisn.notes.ui.reminder

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.common.ConfirmDialog

class ReminderPermission(
    val fragment: Fragment,
    val context: Context
) : ConfirmDialog.Callback {

    private var requestPermissionLauncher: ActivityResultLauncher<String>? = null
    private var permissionRequested = false

    /**
     * Optional listener called if permission was denied or may have been denied.
     */
    var deniedListener: (() -> Unit)? = null

    init {
        requestPermissionLauncher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                if (permissionRequested) {
                    // Explanation was just shown, user denied permission.
                    deniedListener?.invoke()
                } else {
                    // Ask user to go to the app's alarm&reminders settings to grant the permission.
                    // Only do this if the permission wasn't requested just before.
                    ConfirmDialog.newInstance(
                        message = R.string.reminder_alarm_permission,
                        btnPositive = R.string.action_ok,
                    ).show(fragment.childFragmentManager,
                        ReminderPermission.REMINDER_PERMISSION_DENIED_DIALOG
                    )
                }
            }
        }
    }

    fun request() {
        if (Build.VERSION.SDK_INT < 34) {
            return
        }
        val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            alarmManager.canScheduleExactAlarms() -> {
                // OK, permission already granted.
            }

            fragment.shouldShowRequestPermissionRationale(Manifest.permission.SCHEDULE_EXACT_ALARM) -> {
                // Show dialog explaning permission request.
                ConfirmDialog.newInstance(
                    message = R.string.reminder_alarm_permission,
                    btnPositive = R.string.action_ok,
                ).show(fragment.childFragmentManager,
                    REMINDER_PERMISSION_DIALOG
                )
                permissionRequested = true
            }

            else -> {
                requestPermissionLauncher?.launch(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (Build.VERSION.SDK_INT < 34) {
            return
        }

        when (tag) {
            REMINDER_PERMISSION_DIALOG -> {
                // First time asking, can request normally.
                requestPermissionLauncher?.launch(Manifest.permission.SCHEDULE_EXACT_ALARM)
            }

            REMINDER_PERMISSION_DENIED_DIALOG -> {
                // Not first time asking, open alarm&reminders settings window to let user do it.
                val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, fragment.requireContext().packageName)
                fragment.startActivity(settingsIntent)
                // Dismiss immediately since at this point we can't know if user did enable reminders or not.
                deniedListener?.invoke()
            }
        }
    }

    override fun onDialogNegativeButtonClicked(tag: String?) {
        if (tag == REMINDER_PERMISSION_DIALOG || tag == REMINDER_PERMISSION_DENIED_DIALOG) {
            // Notification permission was denied, no point in setting reminder.
            deniedListener?.invoke()
        }
    }

    override fun onDialogCancelled(tag: String?) {
        onDialogNegativeButtonClicked(tag)
    }

    companion object {
        private const val REMINDER_PERMISSION_DIALOG = "alarm-permission-dialog"
        private const val REMINDER_PERMISSION_DENIED_DIALOG = "alarm-permission-denied-dialog"
    }
}