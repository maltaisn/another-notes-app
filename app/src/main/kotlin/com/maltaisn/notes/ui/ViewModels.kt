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

package com.maltaisn.notes.ui

import androidx.activity.ComponentActivity
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelStore
import androidx.navigation.findNavController
import com.maltaisn.notes.R

@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.hiltNavGraphViewModels(
    @IdRes navGraphId: Int,
    @IdRes navHostId: Int = R.id.nav_host_fragment,
): Lazy<VM> {
    val backStackEntry by lazy {
        findNavController(navHostId).getBackStackEntry(navGraphId)
    }
    val storeProducer: () -> ViewModelStore = {
        backStackEntry.viewModelStore
    }
    return ViewModelLazy(
        VM::class, storeProducer,
        factoryProducer = {
            HiltViewModelFactory(this, backStackEntry.defaultViewModelProviderFactory)
        },
        extrasProducer = { backStackEntry.defaultViewModelCreationExtras }
    )
}
