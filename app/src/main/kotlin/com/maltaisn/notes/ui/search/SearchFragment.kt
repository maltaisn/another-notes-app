/*
 * Copyright 2023 Nicolas Maltais
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
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.transition.MaterialElevationScale
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.showKeyboard
import com.maltaisn.notes.ui.note.NoteFragment
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import com.google.android.material.R as RMaterial

class SearchFragment : NoteFragment() {

    @Inject
    lateinit var viewModelFactory: SearchViewModel.Factory

    override val viewModel by viewModel { viewModelFactory.create(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)

        enterTransition = MaterialElevationScale(false).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
        exitTransition = MaterialElevationScale(true).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        // Toolbar
        val toolbar = binding.toolbar
        toolbar.apply {
            inflateMenu(R.menu.toolbar_search)
            setNavigationIcon(R.drawable.ic_arrow_start)
            setNavigationContentDescription(R.string.content_descrp_back)
            setNavigationOnClickListener {
                view.hideKeyboard()
                navController.popBackStack()
            }
        }

        binding.fab.isVisible = false

        // Recycler view
        val rcv = binding.recyclerView
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

        // Disable lift on scroll so that the toolbar is always a different color than the background.
        binding.toolbarLayout.isLiftOnScroll = false

        // Focus search view when search fragment is shown.
        searchView.setOnQueryTextFocusChangeListener { editText, hasFocus ->
            if (hasFocus) {
                editText.showKeyboard()
            }
        }
        searchView.requestFocus()
    }
}
