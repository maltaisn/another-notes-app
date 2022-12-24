/*
 * Copyright 2022 Nicolas Maltais
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

import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.maltaisn.notes.listNote
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.testNote
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.util.Locale

object ScreenshotHelper {

    /**
     * Get a note by ID, in notes described in androidTest/res/values-xx/strings.xml
     * See the strings file for more details.
     */
    fun getNote(id: Long): Note {
        val context = InstrumentationRegistry.getInstrumentation().context
        val strId = context.resources.getIdentifier("screenshot_note$id", "string", context.packageName)
        check(strId != 0) { "String `screenshot_note$id` doesn't exist, can't create note!" }
        val str = context.resources.getString(strId)

        val lines = str.lines()
        // first and last line are expected to contain the quote " hence be blank
        check(lines.first().isBlank())
        check(lines.last().isBlank())

        val title = lines[1].trim().takeIf { it != "_" }.orEmpty()
        val line2 = lines.getOrNull(2).orEmpty().trim()
        return if (line2.startsWith("- ") || line2.startsWith("+ ")) {
            listNote(id = id, title = title, items = lines.subList(2, lines.size - 1).map {
                val item = it.trim()
                check(item.startsWith("- ") || item.startsWith("+ ")) { "Invalid list item" }
                ListNoteItem(item.substring(2), item.startsWith('+'))
            })
        } else {
            testNote(id = id, title = title, content = lines.subList(2, lines.size - 1)
                .joinToString("\n") { it.trim() })
        }
    }

    /**
     * Get a label by ID, in labels described in androidTest/res/values-xx/strings.xml
     * See the strings file for more details.
     */
    fun getLabel(id: Long): Label {
        val context = InstrumentationRegistry.getInstrumentation().context
        val strId = context.resources.getIdentifier("screenshot_label$id", "string", context.packageName)
        check(strId != 0) { "String `screenshot_label$id` doesn't exist, can't create note!" }
        return Label(id, context.resources.getString(strId))
    }

    fun takeScreenshot(name: String) {
        val capture = androidx.test.core.app.takeScreenshot()

        val context = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "screenshot_")
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        val fos = resolver.openOutputStream(imageUri)
        fos.use {
            capture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

class ScreenshotTestRule : TestRule {

    private val testLocale = getLocaleArgument(ARG_TEST_LOCALE)
    private val endingLocale = getLocaleArgument(ARG_ENDING_LOCALE)

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    if (testLocale != null) {
                        changeLocale(testLocale, Locale.US)
                    }
                    base.evaluate()
                } finally {
                    if (endingLocale != null) {
                        changeLocale(endingLocale)
                    }
                }
            }
        }
    }

    private fun getLocaleArgument(arg: String): Locale? {
        val localeStr = InstrumentationRegistry.getArguments().getString(arg) ?: return null
        return Locale.forLanguageTag(localeStr)
    }

    /**
     * Change the device locales to [locales] at runtime.
     * Taken from [https://stackoverflow.com/a/4683532/5288316],
     * and [https://github.com/fastlane/fastlane/blob/master/screengrab/screengrab-lib/src/main/java/tools.fastlane.screengrab/locale/LocaleUtil.java]
     * Requires `android.permission.CHANGE_CONFIGURATION` permission.
     */
    private fun changeLocale(vararg locales: Locale) {
        Locale.setDefault(locales.first())
        try {
            var amnClass = Class.forName("android.app.ActivityManagerNative")

            // amn = ActivityManagerNative.getDefault();
            val methodGetDefault = amnClass.getMethod("getDefault")
            methodGetDefault.isAccessible = true
            val amn = methodGetDefault.invoke(amnClass)

            // config = amn.getConfiguration();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // getConfiguration moved from ActivityManagerNative to ActivityManagerProxy
                // Used to be: amnClass = Class.forName(amn::class.java.name), doesn't work anymore?
                amnClass = amn::class.java
            }
            val methodGetConfiguration = amnClass.getMethod("getConfiguration")
            methodGetConfiguration.isAccessible = true
            val config = methodGetConfiguration.invoke(amn) as Configuration

            // config.userSetLocale = true;
            val configClass: Class<*> = config::class.java
            val f = configClass.getField("userSetLocale")
            f.setBoolean(config, true)

            // set the locale to the new value
            config.setLocales(LocaleList(*locales.distinct().toTypedArray()))

            // amn.updateConfiguration(config);
            val methodUpdateConfiguration = amnClass.getMethod("updateConfiguration", Configuration::class.java)
            methodUpdateConfiguration.isAccessible = true
            methodUpdateConfiguration.invoke(amn, config)
        } catch (e: Exception) {
            throw RuntimeException("Failed to change locale", e)
        }
    }

    /**
     * Enable System UI demo mode and set dummy status bar data
     * From [https://github.com/fastlane/fastlane/blob/master/screengrab/screengrab-lib/src/main/java/tools.fastlane.screengrab/cleanstatusbar/CleanStatusBar.java].
     */
    fun startStatusBarDemo() {
        sendStatusBarCommand("enter")
        sendStatusBarCommand("clock", "hhmm" to "1000")
        sendStatusBarCommand("battery", "plugged" to "false", "level" to "100")
        sendStatusBarCommand("network", "wifi" to "show", "level" to "4")
        sendStatusBarCommand("network", "mobile" to "show", "datatype" to "none", "level" to "4")
        sendStatusBarCommand("notifications", "visible" to "false")
    }

    fun stopStatusBarDemo() {
        sendStatusBarCommand("exit")
    }

    private fun sendStatusBarCommand(command: String, vararg args: Pair<String, String>) {
        val intent = Intent("com.android.systemui.demo")
        intent.putExtra("command", command)
        for ((key, value) in args) {
            intent.putExtra(key, value)
        }
        InstrumentationRegistry.getInstrumentation().targetContext.sendBroadcast(intent)
    }

    companion object {
        private const val ARG_TEST_LOCALE = "testLocale"
        private const val ARG_ENDING_LOCALE = "endingLocale"
    }
}

