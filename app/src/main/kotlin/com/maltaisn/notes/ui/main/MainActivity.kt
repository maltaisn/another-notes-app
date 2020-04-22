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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.navigation.NavigationView
import com.maltaisn.notes.App
import com.maltaisn.notes.NavGraphDirections
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.ActivityMainBinding
import com.maltaisn.notes.ui.EventObserver
import javax.inject.Inject


class MainActivity : AppCompatActivity() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private val viewModel: MainViewModel by viewModels { viewModelFactory }

    lateinit var drawerLayout: DrawerLayout
    lateinit var navigationView: NavigationView
    private lateinit var navController: NavController

    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
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
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
            val content = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            viewModel.addIntentNote(title, content)
        }

        // Observers
        viewModel.editItemEvent.observe(this, EventObserver { noteId ->
            navController.navigate(NavGraphDirections.actionEditNote(noteId))
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

    override fun onStop() {
        super.onStop()
        viewModel.onStop()
    }

}
