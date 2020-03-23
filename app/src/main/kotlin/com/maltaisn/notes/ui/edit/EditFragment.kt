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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import javax.inject.Inject


class EditFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private val viewModel: EditViewModel by viewModels { viewModelFactory }

    val args: EditFragmentArgs by navArgs()

    private lateinit var toolbar: Toolbar

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, state: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_edit, container, false)

        val context = requireContext()
        val navController = findNavController()

        viewModel.start(args.noteStatus, args.noteId)

        // Setup toolbar
        toolbar = view.findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener {
            navController.popBackStack()
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

        when (args.noteStatus) {
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

        val isTrash = args.noteStatus == NoteStatus.TRASHED
        shareItem.isVisible = !isTrash
        copyItem.isVisible = !isTrash
        deleteItem.setTitle(if (isTrash) {
            R.string.action_delete_forever
        } else {
            R.string.action_delete
        })


        viewModel.noteType.observe(viewLifecycleOwner, Observer {  type ->
            when (type!!) {
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

        return view
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_type -> viewModel.toggleNoteType()
            R.id.item_move -> viewModel.moveNote()
            R.id.item_copy -> viewModel.copyNote()
            R.id.item_delete -> viewModel.deleteNote()
            R.id.item_share -> viewModel.shareNote()
            else -> return false
        }
        return true
    }

}
