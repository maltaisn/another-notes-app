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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.*


class MainFragment : Fragment() {

    private lateinit var toolbar: Toolbar


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, state: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        // Setup toolbar with drawer
        val navController = findNavController()
        toolbar = view.findViewById(R.id.toolbar)
        val drawerLayout: DrawerLayout = requireActivity().findViewById(R.id.drawer_layout)
        toolbar.setupWithNavController(navController, drawerLayout)

        val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.layout_swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            GlobalScope.launch(Dispatchers.Default) {
                delay(2000)
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(context, "Refreshed!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val fab: FloatingActionButton = view.findViewById(R.id.fab)
        fab.setOnClickListener {
            Toast.makeText(context, "Add note", Toast.LENGTH_SHORT).show()
        }

        changeShownNotesStatus(NoteStatus.ACTIVE)

        return view
    }

    fun changeShownNotesStatus(status: NoteStatus) {
        toolbar.setTitle(when (status) {
            NoteStatus.ACTIVE -> R.string.note_location_active
            NoteStatus.ARCHIVED -> R.string.note_location_archived
            NoteStatus.TRASHED -> R.string.note_location_deleted
        })
    }

}
