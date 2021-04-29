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

package com.maltaisn.notes.ui.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.maltaisn.notes.App
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentNavigationBinding
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.navigation.adapter.NavigationAdapter
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider

class NavigationFragment : Fragment() {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var viewModelFactory: NavigationViewModel.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = NavigationAdapter(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager
        rcv.itemAnimator?.changeDuration = 0

        drawerLayout = requireActivity().requireViewById(R.id.drawer_layout)

        setupViewModelObservers(adapter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViewModelObservers(adapter: NavigationAdapter) {
        viewModel.currentHomeDestination.observe(viewLifecycleOwner) { destination ->
            sharedViewModel.changeHomeDestination(destination)
        }

        viewModel.navDirectionsEvent.observeEvent(viewLifecycleOwner) { directions ->
            findNavController().navigateSafe(directions)
        }

        viewModel.navigationItems.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        viewModel.drawerCloseEvent.observeEvent(viewLifecycleOwner) {
            drawerLayout.closeDrawers()
        }
    }

}