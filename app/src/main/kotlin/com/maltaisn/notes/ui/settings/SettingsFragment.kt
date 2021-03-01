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

package com.maltaisn.notes.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.sync.BuildConfig
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentSettingsBinding
import com.maltaisn.notes.ui.AppTheme
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import com.mikepenz.aboutlibraries.LibsBuilder
import javax.inject.Inject
import javax.inject.Provider

class SettingsFragment : PreferenceFragmentCompat(), ConfirmDialog.Callback {

    @Inject
    lateinit var viewModelProvider: Provider<SettingsViewModel>

    private val viewModel by viewModel { viewModelProvider.get() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSettingsBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        viewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT).show()
        }

        viewModel.exportDataEvent.observeEvent(viewLifecycleOwner) { data ->
            val title = getString(R.string.pref_data_export_title)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_SUBJECT, title)
            intent.putExtra(Intent.EXTRA_TITLE, title)
            intent.putExtra(Intent.EXTRA_TEXT, data)
            startActivity(Intent.createChooser(intent, null))
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        requirePreference<DropDownPreference>(PrefsManager.THEME).setOnPreferenceChangeListener { _, theme ->
            (requireContext().applicationContext as App)
                .updateTheme(AppTheme.values().find { it.value == theme }!!)
            true
        }

        requirePreference<Preference>(PrefsManager.EXPORT_DATA).setOnPreferenceClickListener {
            viewModel.exportData()
            true
        }

        requirePreference<Preference>(PrefsManager.CLEAR_DATA).setOnPreferenceClickListener {
            ConfirmDialog.newInstance(
                title = R.string.pref_data_clear,
                message = R.string.pref_data_clear_confirm_message,
                btnPositive = R.string.action_clear
            ).show(childFragmentManager, CLEAR_DATA_DIALOG_TAG)
            true
        }

        requirePreference<Preference>(PrefsManager.VIEW_LICENSES).setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_about_libraries, bundleOf(
                // Navigation component safe args seem to fail for cross module navigation.
                // So pass the customization argument the old way.
                "data" to LibsBuilder().apply {
                    aboutShowIcon = false
                    aboutShowVersion = false
                }
            ))
            true
        }

        // Set version name as summary text for version preference
        requirePreference<Preference>(PrefsManager.VERSION).summary = BuildConfig.VERSION_NAME
    }

    private fun <T : Preference> requirePreference(key: CharSequence) =
        checkNotNull(findPreference<T>(key)) { "Could not find preference with key '$key'." }

    override fun onDialogConfirmed(tag: String?) {
        if (tag == CLEAR_DATA_DIALOG_TAG) {
            viewModel.clearData()
        }
    }

    companion object {
        private const val CLEAR_DATA_DIALOG_TAG = "clear_data_dialog"
        private const val LICENSES_DIALOG_TAG = "licenses_dialog"
    }
}
