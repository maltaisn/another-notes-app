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

package com.maltaisn.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.main.adapter.NoteAdapter
import com.maltaisn.notes.ui.main.adapter.NoteListLayoutMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject


class MainFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private val viewModel: MainViewModel by viewModels { viewModelFactory }

    @Inject lateinit var json: Json

    private lateinit var toolbar: Toolbar

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, state: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        val context = requireContext()
        val navController = findNavController()

        // Setup toolbar with drawer
        toolbar = view.findViewById(R.id.toolbar)
        toolbar.let {
            val drawerLayout: DrawerLayout = requireActivity().findViewById(R.id.drawer_layout)
            it.setNavigationIcon(R.drawable.ic_menu)
            it.setNavigationContentDescription(R.string.content_descrp_open_drawer)
            it.setNavigationOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }
            it.setOnMenuItemClickListener(this)
        }

        // Swipe refresh
        val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.layout_swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.viewModelScope.launch(Dispatchers.Default) {
                delay(2000)
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(context, "Refreshed!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Floating action button
        val fab: FloatingActionButton = view.findViewById(R.id.fab)
        fab.setOnClickListener {
            navController.navigate(MainFragmentDirections.actionMainToEdit())
        }

        // Recycler view
        val rcv: RecyclerView = view.findViewById(R.id.rcv_notes)
        val adapter = NoteAdapter(context, json)
        val layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

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

        viewModel.noteItems.observe(viewLifecycleOwner, Observer { items ->
            adapter.submitList(items)
        })

        viewModel.listLayoutMode.observe(viewLifecycleOwner, Observer { mode ->
            val layoutItem = toolbar.menu.findItem(R.id.item_layout)
            when (mode!!) {
                NoteListLayoutMode.LIST -> {
                    layoutManager.spanCount = 1
                    layoutItem.setIcon(R.drawable.ic_view_grid)
                }
                NoteListLayoutMode.GRID -> {
                    layoutManager.spanCount = 2
                    layoutItem.setIcon(R.drawable.ic_view_list)
                }
            }
            adapter.listLayoutMode = mode
        })

        viewModel.itemClickEvent.observe(viewLifecycleOwner, EventObserver { item ->
            navController.navigate(MainFragmentDirections.actionMainToEdit(item.note.id))
        })

        return view
    }

    fun changeShownNotesStatus(status: NoteStatus) {
        viewModel.setNoteStatus(status)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_search -> Unit
            R.id.item_layout -> viewModel.toggleListLayoutMode()
            R.id.item_empty_trash -> viewModel.emptyTrash()
            else -> return false
        }
        return true
    }

}
