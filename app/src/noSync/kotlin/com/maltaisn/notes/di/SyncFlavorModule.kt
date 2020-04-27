/*
 * Copyright 2020 Nicolas Maltais
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

import com.maltaisn.notes.model.NoSyncNotesRepository
import com.maltaisn.notes.model.NoSyncPrefsManager
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.ui.home.NoSyncNoteRefreshBehavior
import com.maltaisn.notes.ui.home.NoteRefreshBehavior
import com.maltaisn.notes.ui.main.LifecycleBehavior
import com.maltaisn.notes.ui.main.NoSyncLifecycleBehavior
import com.maltaisn.notes.ui.settings.ClearDataBehavior
import com.maltaisn.notes.ui.settings.NoSyncClearDataBehavior
import dagger.Binds
import dagger.Module


@Module
abstract class SyncFlavorModule {

    @Binds
    abstract fun bindsPrefsManager(prefsManager: NoSyncPrefsManager): PrefsManager

    @Binds
    abstract fun bindsNotesRepository(repository: NoSyncNotesRepository): NotesRepository

    @Binds
    abstract fun bindsLifecycleBehavior(behavior: NoSyncLifecycleBehavior): LifecycleBehavior

    @Binds
    abstract fun bindsNoteRefreshBehavior(behavior: NoSyncNoteRefreshBehavior): NoteRefreshBehavior

    @Binds
    abstract fun bindsClearDataBehavior(behavior: NoSyncClearDataBehavior): ClearDataBehavior

}
