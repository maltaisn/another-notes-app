/*
 * Copyright 2025 Nicolas Maltais
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

package com.maltaisn.notes.ui.settings

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceFragmentCompat
import com.maltaisn.notes.databinding.FragmentSettingsBinding
import com.maltaisn.notes.setEnterExitTransitions
import dagger.hilt.android.AndroidEntryPoint

/**
 * Secondary settings fragment for showing nested preference screens.
 * See [https://developer.android.com/guide/topics/ui/settings/organize-your-settings#split_your_hierarchy_into_multiple_screens].
 *
 * Instead of making one fragment per nested screen, this fragment takes an argument indicating
 * the preferences XML to display. The argument is passed via navigation component safe args.
 */
@AndroidEntryPoint
class NestedSettingsFragment : PreferenceFragmentCompat() {

    private val args: NestedSettingsFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEnterExitTransitions()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSettingsBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        binding.toolbar.setTitle(args.prefsTitle)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(args.prefsXml, rootKey)
    }
}
