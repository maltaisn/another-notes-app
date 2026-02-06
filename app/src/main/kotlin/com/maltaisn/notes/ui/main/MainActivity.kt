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

package com.maltaisn.notes.ui.main

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.contains
import androidx.core.view.forEach
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.maltaisn.notes.NavGraphMainDirections
import com.maltaisn.notes.R
import com.maltaisn.notes.TAG
import com.maltaisn.notes.databinding.ActivityMainBinding
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.converter.NoteTypeConverter
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.main.MainViewModel.NewNoteData
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.observeEvent
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    private val sharedViewModel: SharedViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var prefs: PrefsManager

    lateinit var drawerLayout: DrawerLayout

    lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)

        super.onCreate(savedInstanceState)

        // Apply dynamic colors
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        // Can be useful when debugging after process death, debugging notification receiver, etc.
//        Debug.waitForDebugger()

        // For triggering process death during debug
        // Note that the notification permission must be given for venom to work!
//        val venom = Venom.createInstance(this)
//        venom.initialize()
//        venom.start()

        binding = ActivityMainBinding.inflate(layoutInflater)
        drawerLayout = binding.drawerLayout
        setContentView(binding.root)

        enableEdgeToEdge()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this)

        binding.navView.setNavigationItemSelectedListener { item ->
            viewModel.navigationItemSelected(
                item,
                binding.navView.menu.findItem(R.id.drawer_labels).subMenu!!
            )
            true
        }
        viewModel.startPopulatingDrawerWithLabels()

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawers()
            } else {
                // The dispatcher only calls the topmost enabled callback, so temporarily
                // disable it to be able to call the next callback on the stack.
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setupViewModelObservers()
    }

    private fun enableEdgeToEdge() {
        // Allow for transparent status and navigation bars
        // See https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge
        // WindowCompat.enableEdgeToEdge() set transparent system bars, which doesn't work well for the status bar
        // under API 23 and for the navigation bar under API 27 because the contrast is bad in light theme.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= 27 && Build.VERSION.SDK_INT <= 28) {
            // On API 27 & 28, there's no Window.isNavigationBarContrastEnforced that ensures a transparent navigation
            // bar, as configured, has proper constrast. So, we manage the color manually.
            @Suppress("DEPRECATION")
            window.navigationBarColor = MaterialColors.getColor(this, R.attr.navigationBarColorApi27, Color.TRANSPARENT)
        }

        // Apply padding to navigation drawer
        val initialPadding = resources.getDimensionPixelSize(R.dimen.navigation_drawer_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navView.getHeaderView(0).updatePadding(top = sysWindow.top)
            binding.navView.children.last().updatePadding(bottom = initialPadding + sysWindow.bottom)
            if (sysWindow.left > 0) {
                // Add left padding to the navigation view to avoid showing it under the navigation bar.
                // This occurs only in landscape mode with the navigation bar on the left.
                binding.navView.updatePadding(left = sysWindow.left)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupViewModelObservers() {
        val menu = binding.navView.menu
        val labelSubmenu = menu.findItem(R.id.drawer_labels).subMenu!!
        var currentHomeDestination: HomeDestination = HomeDestination.Status(NoteStatus.ACTIVE)

        viewModel.currentHomeDestination.observe(this) { newHomeDestination ->
            sharedViewModel.changeHomeDestination(newHomeDestination)
            currentHomeDestination = newHomeDestination
        }

        viewModel.navDirectionsEvent.observeEvent(this) { navDirections ->
            navController.navigateSafe(navDirections)
        }

        viewModel.drawerCloseEvent.observeEvent(this) {
            drawerLayout.closeDrawers()
        }

        viewModel.clearLabelsEvent.observeEvent(this) {
            labelSubmenu.clear()
        }

        viewModel.labelsAddEvent.observeEvent(this) { labels ->
            if (labels != null) {
                for (label in labels) {
                    labelSubmenu.add(Menu.NONE, View.generateViewId(), Menu.NONE, label.name)
                        .setIcon(R.drawable.ic_label_outline).isCheckable = true
                }
            }

            // Select the current label in the navigation drawer, if it isn't already.
            if (currentHomeDestination is HomeDestination.Labels) {
                val currentLabelName = (currentHomeDestination as HomeDestination.Labels).label.name
                if (binding.navView.checkedItem != null && (
                            binding.navView.checkedItem!! !in labelSubmenu ||
                                    binding.navView.checkedItem!!.title != currentLabelName)
                    || binding.navView.checkedItem == null
                ) {
                    labelSubmenu.forEach { item: MenuItem ->
                        if (item.title == currentLabelName) {
                            binding.navView.setCheckedItem(item)
                            return@forEach
                        }
                    }
                }
            }
        }

        viewModel.manageLabelsVisibility.observe(this) { isVisible ->
            menu.findItem(R.id.drawer_item_edit_labels).isVisible = isVisible
        }

        viewModel.editItemEvent.observeEvent(this) { noteId ->
            // Allow navigating to same destination, in case notification is clicked while already editing a note.
            // In this case the EditFragment will be opened multiple times.
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId), true)
        }

        viewModel.autoExportEvent.observeEvent(this) { uri ->
            viewModel.autoExport(try {
                // write and *truncate*. Otherwise the file is not overwritten!
                contentResolver.openOutputStream(uri.toUri(), "wt")
            } catch (e: Exception) {
                Log.i(TAG, "Auto data export failed", e)
                null
            })
        }

        viewModel.createNoteEvent.observeEvent(this) { newNoteData ->
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(
                type = newNoteData.type.value,
                title = newNoteData.title,
                content = newNoteData.content
            ))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        if (destination.id == R.id.fragment_home) {
            drawerLayout.requestDisallowInterceptTouchEvent(false)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } else {
            drawerLayout.requestDisallowInterceptTouchEvent(true)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
    }

    override fun onStart() {
        super.onStart()

        // Go to label, if it has been newly created
        sharedViewModel.labelAddEventNav.observeEvent(this) { label ->
            if (navController.previousBackStackEntry?.destination?.id == R.id.fragment_home) {
                viewModel.selectLabel(label)
            }
        }

        viewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        handleIntent()
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        // Intercept undo/redo shortcut keys for the whole app. Some keyboards will send these keys to use the
        // EditText undo manager added on API 24. Since we have our own undo, the undo manager is disabled,
        // and we can intercept these keys and to do a proper undo/redo instead.

        // On API 35+, the system has a more complete support for undo/redo, which is not interceptible *at all*.
        // The key events are never sent to the app if undo/redo occurs, it this messes up the app's undo system.
        // There's nothing that can be done. See https://stackoverflow.com/questions/79722988
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    sharedViewModel.undo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    sharedViewModel.redo()
                    return true
                }
            }
        } else if (event.hasModifiers(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    sharedViewModel.redo()
                    return true
                }
            }
        }
        return super.onKeyShortcut(keyCode, event)
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
                    val noteData = createNoteFromIntent(intent)
                    if (noteData != null) {
                        viewModel.createNote(noteData)
                    }
                }
                Intent.ACTION_APPLICATION_PREFERENCES -> {
                    viewModel.openSettings();
                }
                INTENT_ACTION_CREATE -> {
                    // Intent to create a note of a certain type. Used by launcher shortcuts.
                    val type = NoteTypeConverter.toType(
                        intent.getIntExtra(EXTRA_NOTE_TYPE, 0))
                    viewModel.createNote(NewNoteData(type))
                }
                INTENT_ACTION_EDIT -> {
                    // Intent to edit a specific note. This is used by reminder notification.
                    viewModel.editNote(intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID))
                }
                INTENT_ACTION_SHOW_REMINDERS -> {
                    // Show reminders screen in HomeFragment. Used by launcher shortcut.
                    binding.navView.menu.findItem(R.id.drawer_item_reminders).isChecked = true
                    sharedViewModel.changeHomeDestination(HomeDestination.Reminders)
                }
            }

            // Mark intent as handled or it will be handled again if activity is resumed again.
            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    private fun createNoteFromIntent(intent: Intent): NewNoteData? {
        val extras = intent.extras ?: return null
        var noteData: NewNoteData? = null
        if (intent.type == "text/plain") {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
                // A file was shared
                @Suppress("DEPRECATION")
                val uri = extras.get(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    try {
                        val reader = InputStreamReader(contentResolver.openInputStream(uri))
                        val title = uri.pathSegments.last()
                        val content = reader.readText()
                        noteData = NewNoteData(NoteType.TEXT, title, content)
                        reader.close()
                    } catch (_: IOException) {
                        // nothing to do (file doesn't exist, access error, etc)
                    }
                }
            } else {
                // Text was shared
                val title = extras.getString(Intent.EXTRA_TITLE)
                    ?: extras.getString(Intent.EXTRA_SUBJECT) ?: ""
                val content = extras.getString(Intent.EXTRA_TEXT) ?: ""
                noteData = NewNoteData(NoteType.TEXT, title, content)
            }
        }
        return noteData
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "com.maltaisn.notes.INTENT_HANDLED"

        const val EXTRA_NOTE_TYPE = "com.maltaisn.notes.NOTE_TYPE"

        const val INTENT_ACTION_CREATE = "com.maltaisn.notes.CREATE"
        const val INTENT_ACTION_EDIT = "com.maltaisn.notes.EDIT"
        const val INTENT_ACTION_SHOW_REMINDERS = "com.maltaisn.notes.SHOW_REMINDERS"
    }
}
