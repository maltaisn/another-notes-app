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

package com.maltaisn.notes.ui.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.common.ConfirmDialog

/**
 * Helper class used to request the notification permission for reminders on API 33+.
 * [ConfirmDialog.Callback] methods must be delegated to this class.
 *
 * Useful documentation:
 * - [https://developer.android.com/develop/ui/views/notifications/notification-permission]
 * - [https://developer.android.com/training/permissions/requesting]
 */
class NotificationPermission(
    val fragment: Fragment
) : ConfirmDialog.Callback {

    private var requestPermissionLauncher: ActivityResultLauncher<String>? = null
    private var permissionRequested = false

    /**
     * Optional listener called if permission was denied or may have been denied.
     */
    var deniedListener: (() -> Unit)? = null

    init {
        requestPermissionLauncher = fragment.registerForActivityResult(RequestPermission()) { isGranted ->
            if (!isGranted) {
                if (permissionRequested) {
                    // Explanation was just shown, user denied permission.
                    deniedListener?.invoke()
                } else {
                    // Ask user to go to the app's notification settings to grant the permission.
                    // Only do this if the permission wasn't requested just before.
                    ConfirmDialog.newInstance(
                        message = R.string.reminder_notif_permission,
                        btnPositive = R.string.action_ok,
                    ).show(fragment.childFragmentManager, NOTIF_PERMISSION_DENIED_DIALOG)
                }
            }
        }
    }

    fun request() {
        if (Build.VERSION.SDK_INT < 33) {
            return
        }

        when {
            ContextCompat.checkSelfPermission(fragment.requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                // OK, permission already granted.
            }

            fragment.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Show dialog explaning permission request.
                ConfirmDialog.newInstance(
                    message = R.string.reminder_notif_permission,
                    btnPositive = R.string.action_ok,
                ).show(fragment.childFragmentManager, NOTIF_PERMISSION_DIALOG)
                permissionRequested = true
            }

            else -> {
                requestPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (Build.VERSION.SDK_INT < 33) {
            return
        }

        when (tag) {
            NOTIF_PERMISSION_DIALOG -> {
                // First time asking, can request normally.
                requestPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            NOTIF_PERMISSION_DENIED_DIALOG -> {
                // Not first time asking, open notification settings window to let user do it.
                val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, fragment.requireContext().packageName)
                fragment.startActivity(settingsIntent)
                // Dismiss immediately since at this point we can't know if user did enable notifications or not.
                deniedListener?.invoke()
            }
        }
    }

    override fun onDialogNegativeButtonClicked(tag: String?) {
        if (tag == NOTIF_PERMISSION_DIALOG || tag == NOTIF_PERMISSION_DENIED_DIALOG) {
            // Notification permission was denied, no point in setting reminder.
            deniedListener?.invoke()
        }
    }

    override fun onDialogCancelled(tag: String?) {
        onDialogNegativeButtonClicked(tag)
    }

    companion object {
        private const val NOTIF_PERMISSION_DIALOG = "notif-permission-dialog"
        private const val NOTIF_PERMISSION_DENIED_DIALOG = "notif-permission-denied-dialog"
    }
}
