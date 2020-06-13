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

package com.maltaisn.notes.ui.note

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.NavGraphDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentNoteBinding
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.activityViewModel
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.startSharingData
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Provider


/**
 * This fragment provides common code for home and search fragments.
 */
abstract class NoteFragment : Fragment(), ActionMode.Callback, ConfirmDialog.Callback {

    @Inject lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by activityViewModel { sharedViewModelProvider.get() }

    protected abstract val viewModel: NoteViewModel

    private var _binding: FragmentNoteBinding? = null
    protected val binding get() = _binding!!

    protected var actionMode: ActionMode? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        // Recycler view
        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = NoteAdapter(context, viewModel)
        val layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        // Observers
        viewModel.noteItems.observe(viewLifecycleOwner, Observer { items ->
            adapter.submitList(items)
        })

        viewModel.listLayoutMode.observe(viewLifecycleOwner, Observer { mode ->
            layoutManager.spanCount = resources.getInteger(when (mode!!) {
                NoteListLayoutMode.LIST -> R.integer.note_list_layout_span_count
                NoteListLayoutMode.GRID -> R.integer.note_grid_layout_span_count
            })
            adapter.listLayoutMode = mode
        })

        viewModel.editItemEvent.observe(viewLifecycleOwner, EventObserver { noteId ->
            findNavController().navigateSafe(NavGraphDirections.actionEditNote(noteId))
        })

        viewModel.currentSelection.observe(viewLifecycleOwner, Observer { selection ->
            if (selection.count != 0 && actionMode == null) {
                actionMode = binding.toolbar.startActionMode(this)

            } else if (selection.count == 0 && actionMode != null) {
                actionMode?.finish()
                actionMode = null
            }

            actionMode?.let {
                it.title = NUMBER_FORMAT.format(selection.count)

                // Share and copy are only visible if there is a single note selected.
                val menu = it.menu
                val singleSelection = selection.count == 1
                menu.findItem(R.id.item_share).isVisible = singleSelection
                menu.findItem(R.id.item_copy).isVisible = singleSelection

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
                    NoteStatus.TRASHED -> {
                        moveItem.setIcon(R.drawable.ic_restore)
                        moveItem.setTitle(R.string.action_restore)
                        deleteItem.setTitle(R.string.action_delete_forever)
                    }
                }
            }
        })

        viewModel.shareEvent.observe(viewLifecycleOwner, EventObserver { data ->
            startSharingData(data)
        })

        viewModel.statusChangeEvent.observe(viewLifecycleOwner, EventObserver { statusChange ->
            sharedViewModel.onStatusChange(statusChange)
        })

        viewModel.placeholderData.observe(viewLifecycleOwner, Observer { data ->
            binding.placeholderGroup.isVisible = data != null
            if (data != null) {
                binding.placeholderImv.setImageResource(data.iconId)
                binding.placeholderTxv.setText(data.messageId)
            }
        })

        viewModel.showDeleteConfirmEvent.observe(viewLifecycleOwner, EventObserver {
            ConfirmDialog.newInstance(
                    title = R.string.action_delete_selection_forever,
                    message = R.string.trash_delete_selected_message,
                    btnPositive = R.string.action_delete
            ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
        })

        sharedViewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { messageId ->
            Snackbar.make(view, messageId, Snackbar.LENGTH_SHORT).show()
        })
        sharedViewModel.statusChangeEvent.observe(viewLifecycleOwner, EventObserver { statusChange ->
            val messageId = when (statusChange.newStatus) {
                NoteStatus.ACTIVE -> if (statusChange.oldStatus == NoteStatus.TRASHED) {
                    R.plurals.edit_message_move_restore
                } else {
                    R.plurals.edit_message_move_unarchive
                }
                NoteStatus.ARCHIVED -> R.plurals.edit_move_archive_message
                NoteStatus.TRASHED -> R.plurals.edit_message_move_delete
            }
            val count = statusChange.oldNotes.size
            val message = context.resources.getQuantityString(messageId, count, count)

            Snackbar.make(view, message, STATUS_CHANGE_SNACKBAR_DURATION)
                    .setAction(R.string.action_undo) {
                        sharedViewModel.undoStatusChange()
                    }.show()
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
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
        mode.menuInflater.inflate(R.menu.cab_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        viewModel.clearSelection()
    }

    override fun onDialogConfirmed(tag: String?) {
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
