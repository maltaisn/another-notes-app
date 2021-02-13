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

package com.maltaisn.notes.ui.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.findNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.BuildConfig
import com.maltaisn.notes.sync.NavGraphMainDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.note.NoteFragment
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject

/**
 * Start screen fragment displaying a list of note for different note statuses.
 */
class HomeFragment : NoteFragment(), Toolbar.OnMenuItemClickListener,
    NavigationView.OnNavigationItemSelectedListener {

    @Inject
    lateinit var viewModelFactory: HomeViewModel.Factory
    override val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()
        (context.applicationContext as App).appComponent.inject(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Detect battery restriction as it affects reminder alarms.
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as? ActivityManager
            if (activityManager?.isBackgroundRestricted == true) {
                viewModel.notifyBatteryRestricted()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar
        binding.toolbar.apply {
            inflateMenu(R.menu.toolbar_home)
            setOnMenuItemClickListener(this@HomeFragment)
            setNavigationIcon(R.drawable.ic_menu)
            setNavigationContentDescription(R.string.content_descrp_open_drawer)
            setNavigationOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            // Hide or show build type and flavor specific items
            menu.findItem(R.id.item_extra_action).isVisible = BuildConfig.DEBUG
        }

        // Floating action button
        binding.fab.setOnClickListener {
            findNavController().navigateSafe(NavGraphMainDirections.actionEditNote())
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        viewModel.destination.observe(viewLifecycleOwner) { destination ->
            updateToolbarForDestination(destination)
            updateFabForDestination(destination)
        }

        viewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT).show()
        }

        viewModel.listLayoutMode.observe(viewLifecycleOwner) { mode ->
            updateListLayoutItemForMode(mode ?: return@observe)
        }

        viewModel.showEmptyTrashDialogEvent.observeEvent(viewLifecycleOwner) {
            showEmptyTrashConfirmDialog()
        }
    }

    private fun updateToolbarForDestination(destination: HomeDestination) {
        // Show "Empty recycle bin" toolbar option
        binding.toolbar.menu.findItem(R.id.item_empty_trash).isVisible =
            destination == HomeDestination.DELETED

        // Update toolbar title
        binding.toolbar.setTitle(when (destination) {
            HomeDestination.ACTIVE -> R.string.note_location_active
            HomeDestination.ARCHIVED -> R.string.note_location_archived
            HomeDestination.DELETED -> R.string.note_location_deleted
            HomeDestination.REMINDERS -> R.string.note_reminders
        })
    }

    private fun updateFabForDestination(destination: HomeDestination) {
        // Fab is only shown in active notes.
        if (destination == HomeDestination.ACTIVE) {
            binding.fab.show()
        } else {
            binding.fab.hide()
        }
    }

    private fun updateListLayoutItemForMode(mode: NoteListLayoutMode) {
        val layoutItem = binding.toolbar.menu.findItem(R.id.item_layout)
        when (mode) {
            NoteListLayoutMode.LIST -> {
                layoutItem.setIcon(R.drawable.ic_view_grid)
                layoutItem.setTitle(R.string.action_layout_grid)
            }
            NoteListLayoutMode.GRID -> {
                layoutItem.setIcon(R.drawable.ic_view_list)
                layoutItem.setTitle(R.string.action_layout_list)
            }
        }
    }

    private fun showEmptyTrashConfirmDialog() {
        ConfirmDialog.newInstance(
            title = R.string.action_empty_trash,
            message = R.string.trash_empty_message,
            btnPositive = R.string.action_empty_trash_short
        ).show(childFragmentManager, EMPTY_TRASH_DIALOG_TAG)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_search -> findNavController().navigateSafe(
                HomeFragmentDirections.actionHomeToSearch())
            R.id.item_layout -> viewModel.toggleListLayoutMode()
            R.id.item_empty_trash -> viewModel.emptyTrashPre()
            R.id.item_extra_action -> viewModel.doExtraAction()
            else -> return false
        }
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        super.onNavigationItemSelected(item)

        when (item.itemId) {
            R.id.item_location_active -> viewModel.setDestination(HomeDestination.ACTIVE)
            R.id.item_location_archived -> viewModel.setDestination(HomeDestination.ARCHIVED)
            R.id.item_location_deleted -> viewModel.setDestination(HomeDestination.DELETED)
            R.id.item_reminder_list -> viewModel.setDestination(HomeDestination.REMINDERS)
            R.id.item_settings -> findNavController().navigateSafe(
                HomeFragmentDirections.actionHomeToSettings())
            else -> return false
        }

        return true
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
