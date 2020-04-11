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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.note.NoteFragment
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode


class HomeFragment : NoteFragment(), Toolbar.OnMenuItemClickListener {

    override val viewModel: HomeViewModel by viewModels { viewModelFactory }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        // Toolbar
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        toolbar.setOnMenuItemClickListener(this)

        // Swipe refresh
        val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.layout_swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.syncNotes()
        }

        // Floating action button
        val fab: FloatingActionButton = view.findViewById(R.id.fab)
        fab.setOnClickListener {
            navController.navigate(HomeFragmentDirections.actionHomeToEdit())
        }

        // Observers
        viewModel.noteStatus.observe(viewLifecycleOwner, Observer { status ->
            // Show "Empty recycle bin" toolbar option
            toolbar.menu.findItem(R.id.item_empty_trash).isVisible = status == NoteStatus.TRASHED

            // Update toolbar title
            toolbar.setTitle(when (status!!) {
                NoteStatus.ACTIVE -> R.string.note_location_active
                NoteStatus.ARCHIVED -> R.string.note_location_archived
                NoteStatus.TRASHED -> R.string.note_location_deleted
            })

            // Fab is only shown in active notes.
            if (status == NoteStatus.ACTIVE) {
                fab.show()
            } else {
                fab.hide()
            }
        })

        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { messageId ->
            Snackbar.make(view, messageId, Snackbar.LENGTH_SHORT).show()
        })

        viewModel.stopRefreshEvent.observe(viewLifecycleOwner, EventObserver {
            swipeRefresh.isRefreshing = false
        })

        viewModel.listLayoutMode.observe(viewLifecycleOwner, Observer { mode ->
            val layoutItem = toolbar.menu.findItem(R.id.item_layout)
            layoutItem.setIcon(when (mode!!) {
                NoteListLayoutMode.LIST -> R.drawable.ic_view_grid
                NoteListLayoutMode.GRID -> R.drawable.ic_view_list
            })
        })

        viewModel.editItemEvent.observe(viewLifecycleOwner, EventObserver { item ->
            navController.navigate(HomeFragmentDirections.actionHomeToEdit(item.note.id))
        })

    }

    fun changeShownNotesStatus(status: NoteStatus) {
        viewModel.setNoteStatus(status)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_search -> findNavController().navigate(R.id.action_home_to_search)
            R.id.item_layout -> viewModel.toggleListLayoutMode()
            R.id.item_empty_trash -> viewModel.emptyTrash()
            R.id.item_add_debug_notes -> viewModel.addDebugNotes()
            else -> return false
        }
        return true
    }

}
