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
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.maltaisn.notes.model.DefaultJsonManager
import com.maltaisn.notes.model.DefaultLabelsRepository
import com.maltaisn.notes.model.DefaultNotesRepository
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.ReminderAlarmCallback
import com.maltaisn.notes.receiver.ReceiverAlarmCallback
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json

@Module(includes = [
    DatabaseModule::class,
    BuildTypeModule::class,
])
abstract class AppModule {

    @get:Binds
    abstract val DefaultNotesRepository.bindNotesRepository: NotesRepository

    @get:Binds
    abstract val DefaultLabelsRepository.bindLabelsRepository: LabelsRepository

    @get:Binds
    abstract val DefaultJsonManager.bindJsonManager: JsonManager

    @get:Binds
    abstract val ReceiverAlarmCallback.bindAlarmCallback: ReminderAlarmCallback

    companion object {
        @Provides
        fun providesSharedPreferences(context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        @get:Provides
        val json
            get() = Json {
                encodeDefaults = false
                ignoreUnknownKeys = true
            }
    }
}
