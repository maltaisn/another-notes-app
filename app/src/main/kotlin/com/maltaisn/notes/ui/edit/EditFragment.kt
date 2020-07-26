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
import com.maltaisn.notes.showKeyboard
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentEditBinding
import com.maltaisn.notes.ui.*
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.edit.adapter.EditAdapter
import javax.inject.Inject
import javax.inject.Provider


class EditFragment : Fragment(), Toolbar.OnMenuItemClickListener, ConfirmDialog.Callback {

    @Inject lateinit var viewModelProvider: Provider<EditViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }

    @Inject lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by activityViewModel { sharedViewModelProvider.get() }

    private val args: EditFragmentArgs by navArgs()

    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            viewModel.save()
            viewModel.exit()
        }

        viewModel.start(args.noteId)

        // Toolbar
        val toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener {
            view.hideKeyboard()
            viewModel.save()
            viewModel.exit()
        }
        toolbar.setTitle(if (args.noteId == Note.NO_ID) {
            view.postDelayed(200) {
                view.showKeyboard()
            }
            R.string.edit_add_title
        } else {
            R.string.edit_change_title
        })

        val toolbarMenu = toolbar.menu
        val typeItem = toolbarMenu.findItem(R.id.item_type)
        val moveItem = toolbarMenu.findItem(R.id.item_move)
        val pinItem = toolbarMenu.findItem(R.id.item_pin)
        val shareItem = toolbarMenu.findItem(R.id.item_share)
        val uncheckAllItem = toolbarMenu.findItem(R.id.item_uncheck_all)
        val deleteCheckedItem = toolbarMenu.findItem(R.id.item_delete_checked)
        val copyItem = toolbarMenu.findItem(R.id.item_copy)
        val deleteItem = toolbarMenu.findItem(R.id.item_delete)

        // Recycler view
        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = EditAdapter(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        // Observers
        viewModel.noteStatus.observe(viewLifecycleOwner, Observer { status ->
            if (status != null) {
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
                shareItem.isVisible = !isTrash
                copyItem.isVisible = !isTrash
                deleteItem.setTitle(if (isTrash) {
                    R.string.action_delete_forever
                } else {
                    R.string.action_delete
                })
            }
        })

        viewModel.notePinned.observe(viewLifecycleOwner, Observer { pinned ->
            when (pinned) {
                PinnedStatus.PINNED -> {
                    pinItem.isVisible = true
                    pinItem.setTitle(R.string.action_unpin)
                    pinItem.setIcon(R.drawable.ic_pin_outline)
                }
                PinnedStatus.UNPINNED -> {
                    pinItem.isVisible = true
                    pinItem.setTitle(R.string.action_pin)
                    pinItem.setIcon(R.drawable.ic_pin)
                }
                PinnedStatus.CANT_PIN, null -> {
                    pinItem.isVisible = false
                }
            }
        })

        viewModel.noteType.observe(viewLifecycleOwner, Observer { type ->
            val isList = type == NoteType.LIST
            uncheckAllItem.isVisible = isList
            deleteCheckedItem.isVisible = isList
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
        })

        viewModel.editItems.observe(viewLifecycleOwner, Observer { items ->
            adapter.submitList(items)
        })

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
                EditMessage.RESTORED_NOTE -> Snackbar.make(view, resources.getQuantityText(
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
    }

}
