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

package com.maltaisn.notes.ui.note

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.NavGraphMainDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentNoteBinding
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.main.MainActivity
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.startSharingData
import com.maltaisn.notes.ui.utils.startSafeActionMode
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Provider

/**
 * This fragment provides common code for home and search fragments.
 */
abstract class NoteFragment : Fragment(), ActionMode.Callback, ConfirmDialog.Callback,
    NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    val sharedViewModel: SharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var prefsManager: PrefsManager

    protected abstract val viewModel: NoteViewModel

    private var _binding: FragmentNoteBinding? = null
    protected val binding get() = _binding!!

    private var actionMode: ActionMode? = null

    protected lateinit var drawerLayout: DrawerLayout

    private var hideActionMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        // Drawer
        val activity = requireActivity() as MainActivity
        drawerLayout = activity.drawerLayout

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = NoteAdapter(context, viewModel, prefsManager)
        val layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        findNavController().addOnDestinationChangedListener(this)

        setupViewModelObservers(adapter, layoutManager)
    }

    private fun setupViewModelObservers(
        adapter: NoteAdapter,
        layoutManager: StaggeredGridLayoutManager
    ) {
        val navController = findNavController()

        viewModel.noteItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
        }

        viewModel.listLayoutMode.observe(viewLifecycleOwner) { mode ->
            layoutManager.spanCount = resources.getInteger(when (mode!!) {
                NoteListLayoutMode.LIST -> R.integer.note_list_layout_span_count
                NoteListLayoutMode.GRID -> R.integer.note_grid_layout_span_count
            })
            adapter.listLayoutMode = mode
        }

        viewModel.editItemEvent.observeEvent(viewLifecycleOwner) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId))
        }

        viewModel.currentSelection.observe(viewLifecycleOwner) { selection ->
            updateActionModeForSelection(selection)
            updateItemsForSelection(selection)
        }

        viewModel.shareEvent.observeEvent(viewLifecycleOwner) { data ->
            startSharingData(data)
        }

        viewModel.statusChangeEvent.observeEvent(viewLifecycleOwner) { statusChange ->
            sharedViewModel.onStatusChange(statusChange)
        }

        viewModel.placeholderData.observe(viewLifecycleOwner) { data ->
            binding.placeholderGroup.isVisible = data != null
            if (data != null) {
                binding.placeholderImv.setImageResource(data.iconId)
                binding.placeholderTxv.setText(data.messageId)
            }
        }

        viewModel.showReminderDialogEvent.observeEvent(viewLifecycleOwner) { noteIds ->
            navController.navigateSafe(NavGraphMainDirections.actionReminder(noteIds.toLongArray()))
        }

        viewModel.showLabelsFragmentEvent.observeEvent(viewLifecycleOwner) { noteIds ->
            navController.navigateSafe(NavGraphMainDirections.actionLabel(noteIds.toLongArray()))
        }

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            showDeleteConfirmDialog()
        }

        sharedViewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT).show()
        }
        sharedViewModel.statusChangeEvent.observeEvent(viewLifecycleOwner) { statusChange ->
            showMessageForStatusChange(statusChange)
        }
    }

    private fun updateActionModeForSelection(selection: NoteViewModel.NoteSelection) {
        if (selection.count != 0 && actionMode == null) {
            actionMode = binding.toolbar.startSafeActionMode(this)
        } else if (selection.count == 0 && actionMode != null) {
            actionMode?.finish()
            actionMode = null
        }
    }

    private fun updateItemsForSelection(selection: NoteViewModel.NoteSelection) {
        actionMode?.let {
            it.title = NUMBER_FORMAT.format(selection.count)

            // Share and copy are only visible if there is a single note selected.
            val menu = it.menu
            val copyShareVisible = selection.count == 1 && selection.status != NoteStatus.DELETED
            menu.findItem(R.id.item_share).isVisible = copyShareVisible
            menu.findItem(R.id.item_copy).isVisible = copyShareVisible

            // Pin item
            val pinItem = menu.findItem(R.id.item_pin)
            when (selection.pinned) {
                PinnedStatus.PINNED -> {
                    pinItem.isVisible = true
                    pinItem.setIcon(R.drawable.ic_pin_outline)
                    pinItem.setTitle(R.string.action_unpin)
                }
                PinnedStatus.UNPINNED -> {
                    pinItem.isVisible = true
                    pinItem.setIcon(R.drawable.ic_pin)
                    pinItem.setTitle(R.string.action_pin)
                }
                PinnedStatus.CANT_PIN -> {
                    pinItem.isVisible = false
                }
            }

            // Reminder item
            val reminderItem = menu.findItem(R.id.item_reminder)
            reminderItem.isVisible = (selection.status != NoteStatus.DELETED)
            reminderItem.setTitle(if (selection.hasReminder) {
                R.string.action_reminder_edit
            } else {
                R.string.action_reminder_add
            })

            // Labels item
            val labelsItem = menu.findItem(R.id.item_labels)
            labelsItem.isVisible = (selection.status != NoteStatus.DELETED)

            // Update move items depending on status
            val moveItem = menu.findItem(R.id.item_move)
            val deleteItem = menu.findItem(R.id.item_delete)
            when (selection.status!!) {
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
                NoteStatus.DELETED -> {
                    moveItem.setIcon(R.drawable.ic_restore)
                    moveItem.setTitle(R.string.action_restore)
                    deleteItem.setTitle(R.string.action_delete_forever)
                }
            }
        }
    }

    private fun showDeleteConfirmDialog() {
        ConfirmDialog.newInstance(
            title = R.string.action_delete_selection_forever,
            message = R.string.trash_delete_selected_message,
            btnPositive = R.string.action_delete
        ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
    }

    @SuppressLint("WrongConstant")
    private fun showMessageForStatusChange(statusChange: StatusChange) {
        val messageId = when (statusChange.newStatus) {
            NoteStatus.ACTIVE -> if (statusChange.oldStatus == NoteStatus.DELETED) {
                R.plurals.edit_message_move_restore
            } else {
                R.plurals.edit_message_move_unarchive
            }
            NoteStatus.ARCHIVED -> R.plurals.edit_move_archive_message
            NoteStatus.DELETED -> R.plurals.edit_message_move_delete
        }
        val count = statusChange.oldNotes.size
        val message = requireContext().resources.getQuantityString(messageId, count, count)

        Snackbar.make(requireView(), message, STATUS_CHANGE_SNACKBAR_DURATION)
            .setAction(R.string.action_undo) {
                sharedViewModel.undoStatusChange()
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        viewModel.stopUpdatingList()

        // If navigating to another fragment while there is a selection (action mode shown),
        // action mode will stay shown on top of new fragment. It has to be destroyed to hide it.
        // `actionMode.finish` calls `onDestroyActionMode`, but we don't want to clear the
        // selection, hence the `hideActionMode` flag.
        // When view is recreated, the selection observer will be fired and action mode reshown.
        hideActionMode = (actionMode != null)
        actionMode?.finish()

        findNavController().removeOnDestinationChangedListener(this)
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_pin -> viewModel.togglePin()
            R.id.item_reminder -> viewModel.createReminder()
            R.id.item_labels -> viewModel.changeLabels()
            R.id.item_move -> viewModel.moveSelectedNotes()
            R.id.item_select_all -> viewModel.selectAll()
            R.id.item_share -> viewModel.shareSelectedNote()
            R.id.item_copy -> viewModel.copySelectedNote(
                getString(R.string.edit_copy_untitled_name), getString(R.string.edit_copy_suffix))
            R.id.item_delete -> viewModel.deleteSelectedNotesPre()
            else -> return false
        }
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.cab_note_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        if (!hideActionMode) {
            viewModel.clearSelection()
        }
        hideActionMode = false
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        if (destination.id == R.id.fragment_edit) {
            // If notes are selected and action mode is shown, navigating to edit fragment
            // with reminder notification or with share action won't dismiss the action mode.
            // Must do it manually.
            viewModel.clearSelection()
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (tag == DELETE_CONFIRM_DIALOG_TAG) {
            viewModel.deleteSelectedNotes()
        }
    }

    companion object {
        private val NUMBER_FORMAT = NumberFormat.getInstance()

        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"

        private const val STATUS_CHANGE_SNACKBAR_DURATION = 7500
    }
}

