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
import android.view.ActionMode
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.iterator
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.BuildConfig
import com.maltaisn.notes.NavGraphDirections
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.main.MainActivity
import com.maltaisn.notes.ui.note.NoteFragment
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode


class HomeFragment : NoteFragment(), Toolbar.OnMenuItemClickListener,
        NavigationView.OnNavigationItemSelectedListener {

    override val viewModel: HomeViewModel by viewModels { viewModelFactory }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        // Drawer
        val activity = requireActivity() as MainActivity
        drawerLayout = activity.drawerLayout
        navView = activity.navigationView
        navView.setNavigationItemSelectedListener(this)

        // Toolbar
        val toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.toolbar_home)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationContentDescription(R.string.content_descrp_open_drawer)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Hide or show build type and flavor specific items
        toolbar.menu.findItem(R.id.item_extra_action).isVisible = BuildConfig.DEBUG

        if (BuildConfig.FLAVOR == BuildConfig.FLAVOR_NO_SYNC) {
            for (item in navView.menu) {
                if (item.groupId == R.id.group_sync) {
                    item.isVisible = false
                }
            }
        }

        // Swipe refresh
        val refreshLayout = binding.swipeRefreshLayout
        refreshLayout.setOnRefreshListener {
            viewModel.refreshNotes()
        }

        // Floating action button
        val fab = binding.fab
        fab.setOnClickListener {
            navController.navigate(NavGraphDirections.actionEditNote())
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

        viewModel.currentSelection.observe(viewLifecycleOwner, Observer { selection ->
            if (selection.count != 0 && actionMode == null) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            }
        })

        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { messageId ->
            Snackbar.make(view, messageId, Snackbar.LENGTH_SHORT).show()
        })

        viewModel.canRefresh.observe(viewLifecycleOwner, Observer {
            refreshLayout.isEnabled = it
        })

        viewModel.stopRefreshEvent.observe(viewLifecycleOwner, EventObserver {
            refreshLayout.isRefreshing = false
        })

        viewModel.listLayoutMode.observe(viewLifecycleOwner, Observer { mode ->
            val layoutItem = toolbar.menu.findItem(R.id.item_layout)
            layoutItem.setIcon(when (mode!!) {
                NoteListLayoutMode.LIST -> R.drawable.ic_view_grid
                NoteListLayoutMode.GRID -> R.drawable.ic_view_list
            })
        })

        viewModel.showEmptyTrashDialogEvent.observe(viewLifecycleOwner, EventObserver {
            ConfirmDialog.newInstance(
                    title = R.string.action_empty_trash,
                    message = R.string.trash_empty_message,
                    btnPositive = R.string.action_empty_trash_short
            ).show(childFragmentManager, EMPTY_TRASH_DIALOG_TAG)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        navView.setNavigationItemSelectedListener(null)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_search -> findNavController().navigate(R.id.action_home_to_search)
            R.id.item_layout -> viewModel.toggleListLayoutMode()
            R.id.item_empty_trash -> viewModel.emptyTrashPre()
            R.id.item_extra_action -> viewModel.doExtraAction()
            else -> return false
        }
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_location_active -> viewModel.setNoteStatus(NoteStatus.ACTIVE)
            R.id.item_location_archived -> viewModel.setNoteStatus(NoteStatus.ARCHIVED)
            R.id.item_location_deleted -> viewModel.setNoteStatus(NoteStatus.TRASHED)
            R.id.item_sync -> findNavController().navigate(R.id.action_home_to_sync)
            R.id.item_settings -> findNavController().navigate(R.id.action_home_to_settings)
            else -> return false
        }

        drawerLayout.closeDrawers()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        super.onDestroyActionMode(mode)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
    }

    override fun onDialogConfirmed(tag: String?) {
        super.onDialogConfirmed(tag)
        if (tag == EMPTY_TRASH_DIALOG_TAG) {
            viewModel.emptyTrash()
        }
    }

    companion object {
        private const val EMPTY_TRASH_DIALOG_TAG = "empty_trash_dialog"
    }

}
