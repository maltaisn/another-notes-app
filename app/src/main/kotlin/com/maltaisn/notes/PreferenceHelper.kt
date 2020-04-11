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

package com.maltaisn.notes

import kotlin.time.days
import kotlin.time.minutes
import kotlin.time.seconds


object PreferenceHelper {

    const val LAST_SYNC_TIME = "last_sync_time"

    const val LIST_LAYOUT_MODE = "is_in_list_layout"
    const val LAST_TRASH_REMIND_TIME = "last_deleted_remind_time"

    val MIN_AUTO_SYNC_INTERVAL = 10.minutes
    val MIN_MANUAL_SYNC_INTERVAL = 15.seconds

    val TRASH_AUTO_DELETE_DELAY = 7.days
    val TRASH_REMINDER_DELAY = 60.days

}
