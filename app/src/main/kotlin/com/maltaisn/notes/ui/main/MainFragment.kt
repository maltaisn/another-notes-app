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
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.main.adapter.NoteAdapter
import com.maltaisn.notes.ui.main.adapter.NoteListLayoutMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.NumberFormat
import javax.inject.Inject


class MainFragment : Fragment(), Toolbar.OnMenuItemClickListener, ActionMode.Callback {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private val viewModel: MainViewModel by viewModels { viewModelFactory }
    private val sharedViewModel: SharedViewModel by activityViewModels { viewModelFactory }

    @Inject lateinit var json: Json

    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private var actionMode: ActionMode? = null


    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, state: Bundle?): View =
            inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        val activity = requireActivity()
        val navController = findNavController()

        // Setup toolbar with drawer
        toolbar = view.findViewById(R.id.toolbar)
        drawerLayout = activity.findViewById(R.id.drawer_layout)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationContentDescription(R.string.content_descrp_open_drawer)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        toolbar.setOnMenuItemClickListener(this)

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
        rcv.setHasFixedSize(true)
        val adapter = NoteAdapter(context, json, viewModel)
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

        viewModel.editItemEvent.observe(viewLifecycleOwner, EventObserver { item ->
            navController.navigate(MainFragmentDirections.actionMainToEdit(item.note.id))
        })

        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { message ->
            sharedViewModel.onMessageEvent(message)
        })

        viewModel.selectedCount.observe(viewLifecycleOwner, Observer { count ->
            if (count != 0 && actionMode == null) {
                actionMode = toolbar.startActionMode(this)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)

            } else if (count == 0 && actionMode != null) {
                actionMode?.finish()
                actionMode = null
            }

            actionMode?.let {
                it.title = NUMBER_FORMAT.format(count)

                // Share and copy are only visible if there is a single note selected.
                val menu = it.menu
                val singleSelection = count == 1
                menu.findItem(R.id.item_share).isVisible = singleSelection
                menu.findItem(R.id.item_copy).isVisible = singleSelection
            }
        })

        sharedViewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { message ->
            when (message) {
                is MessageEvent.BlankNoteDiscardEvent -> {
                    Snackbar.make(view, R.string.message_blank_note_discarded, Snackbar.LENGTH_SHORT)
                            .show()
                }
                is MessageEvent.StatusChangeEvent -> {
                    val count = message.statusChange.oldNotes.size
                    Snackbar.make(view, context.resources.getQuantityString(
                            message.messageId, count, count), Snackbar.LENGTH_SHORT)
                            .setAction(R.string.action_undo) {
                                sharedViewModel.undoStatusChange()
                            }.show()
                }
            }
        })
    }

    fun changeShownNotesStatus(status: NoteStatus) {
        viewModel.setNoteStatus(status)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_search -> Unit
            R.id.item_layout -> viewModel.toggleListLayoutMode()
            R.id.item_empty_trash -> viewModel.emptyTrash()
            R.id.item_add_debug_notes -> viewModel.addDebugNotes()
            else -> return false
        }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_move -> viewModel.moveSelectedNotes()
            R.id.item_select_all -> viewModel.selectAll()
            R.id.item_share -> Unit
            R.id.item_copy -> Unit
            R.id.item_delete -> viewModel.deleteSelectedNotes()
            else -> return false
        }
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.selection_cab, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val moveItem = menu.findItem(R.id.item_move)
        val deleteItem = menu.findItem(R.id.item_delete)
        when (viewModel.noteStatus.value!!) {
            NoteStatus.ACTIVE -> {
                moveItem.setIcon(R.drawable.ic_archive)
                moveItem.setTitle(R.string.action_archive)
                deleteItem.setTitle(R.string.action_delete)
            }
            NoteStatus.ARCHIVED -> {
                moveItem.setIcon(R.drawable.ic_unarchive)
                moveItem.setTitle(R.string.action_unarchive)
                deleteItem.setTitle(R.string.action_delete)
            }
            NoteStatus.TRASHED -> {
                moveItem.setIcon(R.drawable.ic_restore)
                moveItem.setTitle(R.string.action_restore)
                deleteItem.setTitle(R.string.action_delete_forever)
            }
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        viewModel.clearSelection()
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
    }

    companion object {
        private val NUMBER_FORMAT = NumberFormat.getInstance()
    }

}
