/*
 * Copyright 2025 Nicolas Maltais
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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.maltaisn.notes.model.DefaultJsonManager
import com.maltaisn.notes.model.DefaultLabelsRepository
import com.maltaisn.notes.model.DefaultNotesRepository
import com.maltaisn.notes.model.DefaultPrefsManager
import com.maltaisn.notes.model.DefaultReminderAlarmManager
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmCallback
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.receiver.ReceiverAlarmCallback
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json

@Module(includes = [
    DatabaseModule::class,
    BuildTypeModule::class,
])
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindNotesRepository(b: DefaultNotesRepository): NotesRepository

    @Binds
    abstract fun bindLabelsRepository(b: DefaultLabelsRepository): LabelsRepository

    @Binds
    abstract fun bindsPrefsManager(b: DefaultPrefsManager): PrefsManager

    @Binds
    abstract fun bindsReminderAlarmManager(b: DefaultReminderAlarmManager): ReminderAlarmManager

    @Binds
    abstract fun bindJsonManager(b: DefaultJsonManager): JsonManager

    @Binds
    abstract fun bindAlarmCallback(b: ReceiverAlarmCallback): ReminderAlarmCallback

    companion object {
        @Provides
        fun providesSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        @Provides
        fun providesJson() = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}
