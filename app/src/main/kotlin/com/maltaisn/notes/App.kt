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

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.maltaisn.notes.di.DaggerAppComponent
import com.maltaisn.notes.ui.settings.PreferenceHelper
import javax.inject.Inject


class App : Application() {

    val appComponent by lazy {
        DaggerAppComponent.factory().create(applicationContext)
    }

    @Inject lateinit var prefs: SharedPreferences


    override fun onCreate() {
        super.onCreate()

        appComponent.inject(this)

        // Set default preference values
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false)
        changeTheme(prefs.getString(PreferenceHelper.THEME, PreferenceHelper.THEME_SYSTEM)!!)
    }

    fun changeTheme(theme: String) {
        AppCompatDelegate.setDefaultNightMode(when (theme) {
            PreferenceHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            PreferenceHelper.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            PreferenceHelper.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> error("Unknown theme")
        })
    }

}
