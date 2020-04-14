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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.ViewModelFragment
import com.maltaisn.notes.ui.edit.adapter.EditAdapter


class EditFragment : ViewModelFragment(), Toolbar.OnMenuItemClickListener {

    private val viewModel: EditViewModel by viewModels { viewModelFactory }
    private val sharedViewModel: SharedViewModel by activityViewModels { viewModelFactory }

    private val args: EditFragmentArgs by navArgs()

    private lateinit var toolbar: Toolbar


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, state: Bundle?): View =
            inflater.inflate(R.layout.fragment_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            viewModel.save()
            viewModel.exit()
        }

        viewModel.start(args.noteId)

        // Toolbar
        toolbar = view.findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener {
            view.hideKeyboard()
            viewModel.save()
            viewModel.exit()
        }
        toolbar.setTitle(if (args.noteId == Note.NO_ID) {
            R.string.edit_add_title
        } else {
            R.string.edit_change_title
        })

        val toolbarMenu = toolbar.menu
        val typeItem = toolbarMenu.findItem(R.id.item_type)
        val moveItem = toolbarMenu.findItem(R.id.item_move)
        val shareItem = toolbarMenu.findItem(R.id.item_share)
        val copyItem = toolbarMenu.findItem(R.id.item_copy)
        val deleteItem = toolbarMenu.findItem(R.id.item_delete)

        // Recycler view
        val rcv: RecyclerView = view.findViewById(R.id.rcv_edit)
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
                    NoteStatus.TRASHED -> {
                        moveItem.setIcon(R.drawable.ic_restore)
                        moveItem.setTitle(R.string.action_restore)
                    }
                }

                val isTrash = status == NoteStatus.TRASHED
                shareItem.isVisible = !isTrash
                copyItem.isVisible = !isTrash
                deleteItem.setTitle(if (isTrash) {
                    R.string.action_delete_forever
                } else {
                    R.string.action_delete
                })
            }
        })

        viewModel.noteType.observe(viewLifecycleOwner, Observer { type ->
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

        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { messageId ->
            Snackbar.make(view, messageId, Snackbar.LENGTH_SHORT).show()
        })

        viewModel.statusChangeEvent.observe(viewLifecycleOwner, EventObserver { statusChange ->
            sharedViewModel.onStatusChange(statusChange)
        })

        viewModel.shareEvent.observe(viewLifecycleOwner, EventObserver { data ->
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TITLE, data.title)
            intent.putExtra(Intent.EXTRA_SUBJECT, data.title)
            intent.putExtra(Intent.EXTRA_TEXT, data.content)
            startActivity(Intent.createChooser(intent, null))
        })

        viewModel.exitEvent.observe(viewLifecycleOwner, EventObserver {
            findNavController().popBackStack()
        })
    }

    override fun onPause() {
        super.onPause()
        viewModel.save()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_type -> viewModel.toggleNoteType()
            R.id.item_move -> viewModel.moveNote()
            R.id.item_copy -> viewModel.copyNote(getString(R.string.edit_copy_untitled_name),
                    getString(R.string.edit_copy_suffix))
            R.id.item_delete -> viewModel.deleteNote()
            R.id.item_share -> viewModel.shareNote()
            else -> return false
        }
        return true
    }

}
