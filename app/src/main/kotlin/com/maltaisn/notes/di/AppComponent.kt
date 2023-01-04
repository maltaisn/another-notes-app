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

package com.maltaisn.notes.di

import android.content.Context
import com.maltaisn.notes.App
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.ui.edit.EditFragment
import com.maltaisn.notes.ui.home.HomeFragment
import com.maltaisn.notes.ui.labels.LabelEditDialog
import com.maltaisn.notes.ui.labels.LabelFragment
import com.maltaisn.notes.ui.main.MainActivity
import com.maltaisn.notes.ui.notification.NotificationActivity
import com.maltaisn.notes.ui.reminder.ReminderDialog
import com.maltaisn.notes.ui.search.SearchFragment
import com.maltaisn.notes.ui.settings.ExportPasswordDialog
import com.maltaisn.notes.ui.settings.ImportPasswordDialog
import com.maltaisn.notes.ui.settings.SettingsFragment
import com.maltaisn.notes.ui.sort.SortDialog
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {

    fun inject(app: App)

    fun inject(activity: MainActivity)
    fun inject(activity: NotificationActivity)

    fun inject(fragment: HomeFragment)
    fun inject(fragment: SearchFragment)
    fun inject(fragment: EditFragment)
    fun inject(fragment: LabelFragment)
    fun inject(fragment: SettingsFragment)
    fun inject(dialog: ReminderDialog)
    fun inject(dialog: LabelEditDialog)
    fun inject(dialog: SortDialog)
    fun inject(dialog: ExportPasswordDialog)
    fun inject(dialog: ImportPasswordDialog)
    fun inject(receiver: AlarmReceiver)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance appContext: Context): AppComponent
    }
}
