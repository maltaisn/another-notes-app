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

package com.maltaisn.notes.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.maltaisn.notes.App
import com.maltaisn.notes.TAG
import com.maltaisn.notes.model.converter.NoteTypeConverter
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.sync.NavGraphMainDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.ActivityMainBinding
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var viewModelProvider: Provider<MainViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }

    lateinit var drawerLayout: DrawerLayout

    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)

        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        // For triggering process death during debug
//        val venom = Venom.createInstance(this)
//        venom.initialize()
//        venom.start()

        binding = ActivityMainBinding.inflate(layoutInflater)
        drawerLayout = binding.drawerLayout
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this)

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        viewModel.editItemEvent.observeEvent(this) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId))
        }

        viewModel.autoExportEvent.observeEvent(this) { uri ->
            viewModel.autoExport(try {
                contentResolver.openOutputStream(Uri.parse(uri))
            } catch (e: Exception) {
                Log.i(TAG, "Auto data export failed", e)
                null
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestinationChanged(ctl: NavController, dst: NavDestination, args: Bundle?) {
        drawerLayout.setDrawerLockMode(if (dst.id == R.id.fragment_home) {
            DrawerLayout.LOCK_MODE_UNLOCKED
        } else {
            DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        })
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        handleIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        navController.removeOnDestinationChangedListener(this)
    }

    private fun handleIntent() {
        val intent = intent ?: return
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    // Plain text was shared to app, create new note for it
                    if (intent.type == "text/plain") {
                        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                        val content = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                        viewModel.createNote(NoteType.TEXT, title, content)
                    }
                }
                INTENT_ACTION_CREATE -> {
                    // Intent to create a note of a certain type. Used by launcher shortcuts.
                    val type = NoteTypeConverter.toType(
                        intent.getIntExtra(EXTRA_NOTE_TYPE, 0))
                    viewModel.createNote(type)
                }
                INTENT_ACTION_EDIT -> {
                    // Intent to edit a specific note. This is used by reminder notification.
                    viewModel.editNote(intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID))
                }
                INTENT_ACTION_SHOW_REMINDERS -> {
                    // Show reminders screen in HomeFragment. Used by launcher shortcut.
                    sharedViewModel.changeHomeDestination(HomeDestination.Reminders)
                }
            }

            // Mark intent as handled or it will be handled again if activity is resumed again.
            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "com.maltaisn.notes.INTENT_HANDLED"

        const val EXTRA_NOTE_TYPE = "com.maltaisn.notes.NOTE_TYPE"

        const val INTENT_ACTION_CREATE = "com.maltaisn.notes.CREATE"
        const val INTENT_ACTION_EDIT = "com.maltaisn.notes.EDIT"
        const val INTENT_ACTION_SHOW_REMINDERS = "com.maltaisn.notes.SHOW_REMINDERS"
    }
}
