/*
 * Copyright 2022 Nicolas Maltais
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

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.maltaisn.notes.App
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.BuildConfig
import com.maltaisn.notes.sync.NavGraphMainDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.note.NoteFragment
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject

/**
 * Start screen fragment displaying a list of notes for different note status,
 * by label, or with a reminder.
 */
class HomeFragment : NoteFragment(), Toolbar.OnMenuItemClickListener {

    @Inject
    lateinit var viewModelFactory: HomeViewModel.Factory
    override val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = requireContext()
        (context.applicationContext as App).appComponent.inject(this)
    }

    override fun onResume() {
        super.onResume()

        val context = requireContext()

        var batteryRestricted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Detect battery restriction as it affects reminder alarms.
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager?.isBackgroundRestricted == true) {
                batteryRestricted = true
            }
        }

        var notificationRestricted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED
            ) {
                notificationRestricted = true
            }
        }

        viewModel.updateRestrictions(batteryRestricted, notificationRestricted)
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
            menu.findItem(R.id.item_extra_action).isVisible = BuildConfig.ENABLE_DEBUG_FEATURES
        }

        // Floating action button
        binding.fab.transitionName = "createNoteTransition"
        binding.fab.setOnClickListener {
            viewModel.createNote()
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        viewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT)
                .setGestureInsetBottomIgnored(true)
                .show()
        }

        viewModel.listLayoutMode.observe(viewLifecycleOwner) { mode ->
            updateListLayoutItemForMode(mode ?: return@observe)
        }

        viewModel.currentSelection.observe(viewLifecycleOwner) { selection ->
            if (selection.count != 0) {
                // Lock drawer when user just selected a first note.
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }

        viewModel.fabShown.observe(viewLifecycleOwner) { shown ->
            if (shown) {
                binding.fab.show()
            } else {
                binding.fab.hide()
            }
        }

        viewModel.createNoteEvent.observeEvent(viewLifecycleOwner) { settings ->
            exitTransition = Hold().apply {
                duration = resources.getInteger(R.integer.material_motion_duration_medium_2).toLong()
            }

            val extras = FragmentNavigatorExtras(
                binding.fab to "noteContainer0"
            )

            findNavController().navigateSafe(NavGraphMainDirections.actionEditNote(
                labelId = settings.labelId, changeReminder = settings.initialReminder), extras = extras)
        }

        viewModel.showEmptyTrashDialogEvent.observeEvent(viewLifecycleOwner) {
            showEmptyTrashConfirmDialog()
        }

        sharedViewModel.currentHomeDestination.observe(viewLifecycleOwner) { destination ->
            viewModel.setDestination(destination)
            updateToolbarForDestination(destination)
        }

        sharedViewModel.sortChangeEvent.observeEvent(viewLifecycleOwner, viewModel::changeSort)
    }

    private fun updateToolbarForDestination(destination: HomeDestination) {
        // Show "Empty recycle bin" toolbar option
        binding.toolbar.menu.findItem(R.id.item_empty_trash).isVisible =
            destination == HomeDestination.Status(NoteStatus.DELETED)

        // Update toolbar title
        binding.toolbar.title = when (destination) {
            is HomeDestination.Status -> when (destination.status) {
                NoteStatus.ACTIVE -> getString(R.string.note_location_active)
                NoteStatus.ARCHIVED -> getString(R.string.note_location_archived)
                NoteStatus.DELETED -> getString(R.string.note_location_deleted)
            }
            is HomeDestination.Labels -> destination.label.name
            is HomeDestination.Reminders -> getString(R.string.note_reminders)
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
            R.id.item_search -> findNavController().navigateSafe(HomeFragmentDirections.actionHomeToSearch())
            R.id.item_layout -> viewModel.toggleListLayoutMode()
            R.id.item_sort -> findNavController().navigateSafe(HomeFragmentDirections.actionHomeToSort())
            R.id.item_empty_trash -> viewModel.emptyTrashPre()
            R.id.item_extra_action -> viewModel.doExtraAction()
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        super.onDestroyActionMode(mode)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        super.onDialogPositiveButtonClicked(tag)
        if (tag == EMPTY_TRASH_DIALOG_TAG) {
            viewModel.emptyTrash()
        }
    }

    companion object {
        private const val EMPTY_TRASH_DIALOG_TAG = "empty_trash_dialog"
    }
}
