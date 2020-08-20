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

package com.maltaisn.notes.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.navigation.NavigationView
import com.maltaisn.notes.App
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.NavGraphDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.ActivityMainBinding
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider

class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelProvider: Provider<MainViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }

    lateinit var drawerLayout: DrawerLayout
    lateinit var navigationView: NavigationView
    private lateinit var navController: NavController

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)

        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        navigationView = binding.navigationView

        navController = findNavController(R.id.nav_host_fragment)
        navController.addOnDestinationChangedListener { _, ds, _ ->
            drawerLayout.setDrawerLockMode(if (ds.id == R.id.fragment_home) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }, GravityCompat.START)
        }

        // Check if activity was opened with a send intent
        val intent = intent
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" &&
            !intent.getBooleanExtra(KEY_INTENT_HANDLED, false)
        ) {
            val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
            val content = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            viewModel.addIntentNote(title, content)

            // Mark intent as handled or it will be handled again when activity restarts.
            intent.putExtra(KEY_INTENT_HANDLED, true)
        }

        // Observers
        viewModel.editItemEvent.observe(this, EventObserver { noteId ->
            navController.navigateSafe(NavGraphDirections.actionEditNote(noteId))
        })
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "intent_handled"
    }
}
