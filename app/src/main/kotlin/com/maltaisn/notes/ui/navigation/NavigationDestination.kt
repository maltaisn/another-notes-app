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

package com.maltaisn.notes.ui.navigation

import android.os.Parcelable
import androidx.navigation.NavDirections
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.home.HomeFragment
import kotlinx.parcelize.Parcelize

/**
 * Different destinations accessible from the navigation drawer.
 */
sealed interface NavigationDestination {
    /**
     * Destination to navigate to another fragment in the navigation graph.
     */
    data class NavGraph(val directions: NavDirections) : NavigationDestination
}

/**
 * A destination accessible only by changing the content of the [HomeFragment].
 */
sealed interface HomeDestination : NavigationDestination, Parcelable {
    /**
     * Destination to view all notes of with a specific [status].
     */
    @Parcelize
    data class Status(val status: NoteStatus) : HomeDestination

    /**
     * Destination to view all notes with a [label].
     */
    @Parcelize
    data class Labels(val label: Label) : HomeDestination

    /**
     * Destination to view all notes with a reminder.
     */
    @Parcelize
    object Reminders : HomeDestination
}
