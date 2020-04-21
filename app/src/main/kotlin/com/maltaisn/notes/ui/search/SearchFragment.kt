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

package com.maltaisn.notes.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.showKeyboard
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.note.NoteFragment


class SearchFragment : NoteFragment() {

    override val viewModel: SearchViewModel by viewModels { viewModelFactory }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()
        
        // Toolbar
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener {
            view.hideKeyboard()
            navController.popBackStack()
        }

        // Recycler view
        val rcv: RecyclerView = view.findViewById(R.id.rcv_notes)
        (rcv.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        // Search view
        val searchView = toolbar.menu.findItem(R.id.item_search_edt).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                view.hideKeyboard()
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                viewModel.searchNotes(query)
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { editText, hasFocus ->
            if (hasFocus) {
                editText.showKeyboard()
            }
        }
        searchView.requestFocus()

        // Observers
        viewModel.editItemEvent.observe(viewLifecycleOwner, EventObserver { item ->
            navController.navigate(SearchFragmentDirections.actionSearchToEdit(item.note.id))
        })
    }

}
