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

package com.maltaisn.notes.ui.labels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.maltaisn.notes.App
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentLabelBinding
import com.maltaisn.notes.ui.labels.adapter.LabelAdapter
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject

class LabelFragment : DialogFragment() {

    @Inject
    lateinit var viewModelFactory: LabelViewModel.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    private var _binding: FragmentLabelBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this);
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentLabelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        binding.toolbar.apply {
            setNavigationIcon(R.drawable.ic_arrow_left)
            setNavigationOnClickListener {
                findNavController().popBackStack()
            }
        }

        binding.fab.setOnClickListener {
            findNavController().navigateSafe(LabelFragmentDirections.actionLabelToLabelAdd())
        }

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = LabelAdapter(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        setupViewModelObservers(adapter)
    }

    private fun setupViewModelObservers(adapter: LabelAdapter) {
        viewModel.labelItems.observe(this) { items ->
            adapter.submitList(items)
        }

        viewModel.placeholderShown.observe(this) { shown ->
            binding.placeholderGroup.isVisible = shown
        }

        viewModel.labelSelection.observe(this) { count ->
            // TODO manage action mode
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}