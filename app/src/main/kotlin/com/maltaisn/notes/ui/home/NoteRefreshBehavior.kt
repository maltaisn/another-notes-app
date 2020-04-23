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

package com.maltaisn.notes.ui.home

import kotlinx.coroutines.channels.ConflatedBroadcastChannel


abstract class NoteRefreshBehavior {

    val canRefreshChannel = ConflatedBroadcastChannel<Boolean>()


    abstract suspend fun start()

    /**
     * Refresh note data. Swipe refresh layout stops refreshing when this method returns.
     * Should return the ID of an error message or `null` if refresh was sucessfull.
     */
    abstract suspend fun refreshNotes(): Int?

}
