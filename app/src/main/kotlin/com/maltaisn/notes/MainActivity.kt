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

package com.maltaisn.notes

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.maltaisn.notes.model.entity.NoteStatus


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        setContentView(R.layout.activity_main)

        // Setup drawer layout
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.drawer_nav_view)
        navView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Get main fragment. If not found, then do nothing since drawer is only accessible from it.
        val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_nav_host) ?: return false
        val mainFragment = navHostFragment.childFragmentManager
                .fragments.first() as? MainFragment ?: return false

        when (item.itemId) {
            R.id.item_location_active -> mainFragment.changeShownNotesStatus(NoteStatus.ACTIVE)
            R.id.item_location_archived -> mainFragment.changeShownNotesStatus(NoteStatus.ARCHIVED)
            R.id.item_location_deleted -> mainFragment.changeShownNotesStatus(NoteStatus.TRASHED)
            R.id.item_sync -> Unit
            R.id.item_settings -> Unit
            else -> return false
        }

        drawerLayout.closeDrawers()

        return true
    }

}
