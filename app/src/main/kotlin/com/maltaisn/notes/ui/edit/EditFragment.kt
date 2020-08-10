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

package com.maltaisn.notes.ui.edit

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.showKeyboard
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentEditBinding
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.activityViewModel
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.edit.adapter.EditAdapter
import com.maltaisn.notes.ui.startSharingData
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider

class EditFragment : Fragment(), Toolbar.OnMenuItemClickListener, ConfirmDialog.Callback {

    @Inject
    lateinit var viewModelProvider: Provider<EditViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by activityViewModel { sharedViewModelProvider.get() }

    private val args: EditFragmentArgs by navArgs()

    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            viewModel.save()
            viewModel.exit()
        }

        viewModel.start(args.noteId)

        // Toolbar
        binding.toolbar.apply {
            setOnMenuItemClickListener(this@EditFragment)
            setNavigationIcon(R.drawable.ic_arrow_left)
            setNavigationOnClickListener {
                view.hideKeyboard()
                viewModel.save()
                viewModel.exit()
            }
            setTitle(if (args.noteId == Note.NO_ID) {
                view.postDelayed(SHOW_KEYBOARD_INITIAL_DELAY) {
                    view.showKeyboard()
                }
                R.string.edit_add_title
            } else {
                R.string.edit_change_title
            })
        }

        // Recycler view
        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = EditAdapter(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        setupViewModelObservers(adapter)
    }

    @SuppressLint("WrongConstant")
    private fun setupViewModelObservers(adapter: EditAdapter) {
        viewModel.noteStatus.observe(viewLifecycleOwner, Observer { status ->
            updateItemsForNoteStatus(status ?: return@Observer)
        })

        viewModel.notePinned.observe(viewLifecycleOwner, Observer { pinned ->
            updateItemsForPinnedStatus(pinned ?: return@Observer)
        })

        viewModel.noteReminder.observe(viewLifecycleOwner, ::updateItemsForReminder)

        viewModel.noteType.observe(viewLifecycleOwner, Observer { type ->
            updateItemsForNoteType(type ?: return@Observer)
        })

        viewModel.editItems.observe(viewLifecycleOwner, adapter::submitList)

        viewModel.focusEvent.observe(viewLifecycleOwner, EventObserver { focus ->
            adapter.setItemFocus(focus)
        })

        val restoreNoteSnackbar by lazy {
            Snackbar.make(requireView(), R.string.edit_in_trash_message, CANT_EDIT_SNACKBAR_DURATION)
                .setAction(R.string.action_restore) { viewModel.restoreNoteAndEdit() }
        }
        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { message ->
            when (message) {
                EditMessage.BLANK_NOTE_DISCARDED -> sharedViewModel.onBlankNoteDiscarded()
                EditMessage.RESTORED_NOTE -> Snackbar.make(requireView(), resources.getQuantityText(
                    R.plurals.edit_message_move_restore, 1), Snackbar.LENGTH_SHORT).show()
                EditMessage.CANT_EDIT_IN_TRASH -> restoreNoteSnackbar.show()
            }
        })

        viewModel.statusChangeEvent.observe(viewLifecycleOwner, EventObserver { statusChange ->
            sharedViewModel.onStatusChange(statusChange)
        })

        viewModel.shareEvent.observe(viewLifecycleOwner, EventObserver { data ->
            startSharingData(data)
        })

        viewModel.showDeleteConfirmEvent.observe(viewLifecycleOwner, EventObserver {
            ConfirmDialog.newInstance(
                title = R.string.action_delete_forever,
                message = R.string.trash_delete_message,
                btnPositive = R.string.action_delete
            ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
        })

        viewModel.showRemoveCheckedConfirmEvent.observe(viewLifecycleOwner, EventObserver {
            ConfirmDialog.newInstance(
                title = R.string.edit_convert_keep_checked,
                btnPositive = R.string.action_delete,
                btnNegative = R.string.action_keep
            ).show(childFragmentManager, REMOVE_CHECKED_CONFIRM_DIALOG_TAG)
        })

        viewModel.exitEvent.observe(viewLifecycleOwner, EventObserver {
            findNavController().popBackStack()
        })
    }

    private fun updateItemsForNoteStatus(status: NoteStatus) {
        val menu = binding.toolbar.menu

        val moveItem = menu.findItem(R.id.item_move)
        when (status) {
            NoteStatus.ACTIVE -> {
                moveItem.setIcon(R.drawable.ic_archive)
                moveItem.setTitle(R.string.action_archive)
            }
            NoteStatus.ARCHIVED -> {
                moveItem.setIcon(R.drawable.ic_unarchive)
                moveItem.setTitle(R.string.action_unarchive)
            }
            NoteStatus.DELETED -> {
                moveItem.setIcon(R.drawable.ic_restore)
                moveItem.setTitle(R.string.action_restore)
            }
        }

        val isTrash = status == NoteStatus.DELETED
        menu.findItem(R.id.item_share).isVisible = !isTrash
        menu.findItem(R.id.item_copy).isVisible = !isTrash
        menu.findItem(R.id.item_delete).setTitle(if (isTrash) {
            R.string.action_delete_forever
        } else {
            R.string.action_delete
        })
    }

    private fun updateItemsForPinnedStatus(pinned: PinnedStatus) {
        val item = binding.toolbar.menu.findItem(R.id.item_pin)
        when (pinned) {
            PinnedStatus.PINNED -> {
                item.isVisible = true
                item.setTitle(R.string.action_unpin)
                item.setIcon(R.drawable.ic_pin_outline)
            }
            PinnedStatus.UNPINNED -> {
                item.isVisible = true
                item.setTitle(R.string.action_pin)
                item.setIcon(R.drawable.ic_pin)
            }
            PinnedStatus.CANT_PIN -> {
                item.isVisible = false
            }
        }
    }

    private fun updateItemsForReminder(reminder: Reminder?) {
        binding.toolbar.menu.findItem(R.id.item_reminder).setTitle(if (reminder != null) {
            R.string.action_reminder_edit
        } else {
            R.string.action_reminder_add
        })
    }

    private fun updateItemsForNoteType(type: NoteType) {
        val menu = binding.toolbar.menu

        val isList = type == NoteType.LIST
        menu.findItem(R.id.item_uncheck_all).isVisible = isList
        menu.findItem(R.id.item_delete_checked).isVisible = isList

        val typeItem = menu.findItem(R.id.item_type)
        when (type) {
            NoteType.TEXT -> {
                typeItem.setIcon(R.drawable.ic_checkbox)
                typeItem.setTitle(R.string.action_convert_to_list)
            }
            NoteType.LIST -> {
                typeItem.setIcon(R.drawable.ic_text)
                typeItem.setTitle(R.string.action_convert_to_text)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.save()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_type -> viewModel.toggleNoteType()
            R.id.item_move -> viewModel.moveNoteAndExit()
            R.id.item_pin -> viewModel.togglePin()
            R.id.item_share -> viewModel.shareNote()
            R.id.item_uncheck_all -> {
                viewModel.uncheckAllItems()
            }
            R.id.item_delete_checked -> viewModel.deleteCheckedItems()
            R.id.item_copy -> viewModel.copyNote(getString(R.string.edit_copy_untitled_name),
                getString(R.string.edit_copy_suffix))
            R.id.item_delete -> viewModel.deleteNote()
            else -> return false
        }
        return true
    }

    override fun onDialogConfirmed(tag: String?) {
        when (tag) {
            DELETE_CONFIRM_DIALOG_TAG -> viewModel.deleteNoteForeverAndExit()
            REMOVE_CHECKED_CONFIRM_DIALOG_TAG -> viewModel.convertToText(false)
        }
    }

    override fun onDialogCancelled(tag: String?) {
        if (tag == REMOVE_CHECKED_CONFIRM_DIALOG_TAG) {
            viewModel.convertToText(true)
        }
    }

    companion object {
        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
        private const val REMOVE_CHECKED_CONFIRM_DIALOG_TAG = "remove_checked_confirm_dialog"

        private const val CANT_EDIT_SNACKBAR_DURATION = 5000

        private const val SHOW_KEYBOARD_INITIAL_DELAY = 200L
    }
}
