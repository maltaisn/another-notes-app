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

package com.maltaisn.notes.screenshot

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.maltaisn.notes.App
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.model.LabelsDao
import com.maltaisn.notes.model.NotesDao
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.edit.adapter.EditItemViewHolder
import com.maltaisn.notes.ui.main.MainActivity
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.ui.note.adapter.NoteViewHolder
import com.maltaisn.notesshared.dateFor
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrenceFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import com.maltaisn.notes.test.R as RT

/**
 * Screenshots should be taken with release build type, or debug features will appear.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore("not a test, comment this annotation to take screenshots")
class Screenshots {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val screenshotTestRule = ScreenshotTestRule()

    private lateinit var context: Context

    private lateinit var notesDao: NotesDao
    private lateinit var labelsDao: LabelsDao

    private lateinit var prefs: PrefsManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<App>()
        context = app

        notesDao = app.database.notesDao()
        labelsDao = app.database.labelsDao()
        prefs = app.prefs
        runBlocking(Dispatchers.Main) {
            // main thread needed for clearing prefs
            notesDao.clear()
            labelsDao.clear()
            prefs.clear(context)
        }
    }

    /**
     * View note (ID 1) in edit screen with keyboard enabled to edit item 2
     * and last modified date shown in fragment.
     */
    @Test
    fun screenshotEdit() = screenshotTest {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PrefsManager.SHOWN_DATE, ShownDateField.MODIFIED.value).apply()
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(addedDate = dateFor("2020-01-01"),
                lastModifiedDate = dateFor("2021-03-23T17:34:00.000Z")),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
        ))

        onView(allOf(isDescendantOfA(withId(R.id.fragment_note_layout)), withId(R.id.recycler_view)))
            .perform(actionOnItemAtPosition<NoteViewHolder<*>>(0, click()))
        delay(250)
        onView(allOf(isDescendantOfA(withId(R.id.fragment_edit_layout)), withId(R.id.recycler_view)))
            .perform(actionOnItemAtPosition<EditItemViewHolder>(3, click()))
        delay(500)

        ScreenshotHelper.takeScreenshot("1")
    }

    /**
     * View home screen with some notes (ID 1, 2, 3, 4), in grid layout mode,
     * with note ID 1 being pinned.
     */
    @Test
    fun screenshotHome() = screenshotTest {
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(pinned = PinnedStatus.PINNED),
            ScreenshotHelper.getNote(2).copy(pinned = PinnedStatus.PINNED),
            ScreenshotHelper.getNote(3),
            ScreenshotHelper.getNote(4),
            ScreenshotHelper.getNote(5),
            ScreenshotHelper.getNote(6),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(2, 1),
            LabelRef(3, 2),
            LabelRef(5, 2),
        ))

        onView(toolbarItem(R.id.item_layout)).perform(click())

        ScreenshotHelper.takeScreenshot("2")
    }

    /**
     * View home screen with some notes (ID 1, 2, 3, 4), in list layout mode,
     * with note ID 1 being selected and its existing reminder edited with dialog.
     * For some unknown reason this test crashes my emulator...
     */
    @Test
    fun screenshotReminder() = screenshotTest {
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(reminder = dummyReminder, pinned = PinnedStatus.PINNED),
            ScreenshotHelper.getNote(2),
            ScreenshotHelper.getNote(3),
            ScreenshotHelper.getNote(5),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insertRefs(listOf(
            LabelRef(2, 1),
            LabelRef(5, 2),
        ))

        onView(allOf(isDescendantOfA(withId(R.id.fragment_note_layout)), withId(R.id.recycler_view)))
            .perform(actionOnItemAtPosition<NoteViewHolder<*>>(1, longClick()))
        delay(250)
        onView(toolbarItem(R.id.item_reminder)).perform(click())

        ScreenshotHelper.takeScreenshot("3")
    }

    /**
     * View search screen with a query matching 2 notes, one active and one archived.
     * The active note has a reminder and the archived note has a label.
     * The keyboard is explicitly hidden.
     */
    @Test
    fun screenshotSearch() = screenshotTest(true) {
        val queryText = InstrumentationRegistry.getInstrumentation()
            .context.getString(RT.string.screenshot_search_query)
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1).copy(reminder = dummyReminder),
            ScreenshotHelper.getNote(2).copy(status = NoteStatus.ARCHIVED, pinned = PinnedStatus.CANT_PIN),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insertRefs(listOf(
            LabelRef(2, 1),
        ))

        onView(toolbarItem(R.id.item_search)).perform(click())
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText(queryText))
        activityRule.scenario.onActivity {
            it.findViewById<View>(androidx.appcompat.R.id.search_src_text).hideKeyboard()
        }
        delay(500)

        ScreenshotHelper.takeScreenshot("4")
    }

    /**
     * View navigation drawer with two labels.
     */
    @Test
    fun screenshotNavigation() = screenshotTest(true) {
        notesDao.insertAll(listOf(
            ScreenshotHelper.getNote(1),
            ScreenshotHelper.getNote(2),
            ScreenshotHelper.getNote(5),
        ))
        labelsDao.insert(ScreenshotHelper.getLabel(1))
        labelsDao.insert(ScreenshotHelper.getLabel(2))
        labelsDao.insert(ScreenshotHelper.getLabel(3))
        labelsDao.insertRefs(listOf(
            LabelRef(1, 1),
            LabelRef(2, 1),
            LabelRef(5, 2),
        ))

        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        delay(250)

        ScreenshotHelper.takeScreenshot("5")
    }

    private val dummyReminder: Reminder
        get() {
            val reminderTime = Calendar.getInstance().apply {
                add(Calendar.DATE, 1)
                this[Calendar.HOUR_OF_DAY] = 8
                this[Calendar.MINUTE] = 0
            }.time
            return Reminder.create(reminderTime, Recurrence(Recurrence.Period.DAILY), RecurrenceFinder())
        }

    private fun toolbarItem(id: Int): Matcher<View> {
        return allOf(withClassName(endsWith("ItemView")), withId(id))
    }

    private inline fun screenshotTest(
        darkMode: Boolean = false,
        crossinline block: suspend CoroutineScope.() -> Unit
    ) {
        runBlocking {
            withContext(Dispatchers.Main) {
                AppCompatDelegate.setDefaultNightMode(if (darkMode) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                })
            }
            screenshotTestRule.startStatusBarDemo()
            block()
            screenshotTestRule.stopStatusBarDemo()
        }
    }
}
