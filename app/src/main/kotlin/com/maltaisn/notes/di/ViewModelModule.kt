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

package com.maltaisn.notes.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.ViewModelFactory
import com.maltaisn.notes.ui.ViewModelKey
import com.maltaisn.notes.ui.edit.EditViewModel
import com.maltaisn.notes.ui.home.HomeViewModel
import com.maltaisn.notes.ui.main.MainViewModel
import com.maltaisn.notes.ui.search.SearchViewModel
import com.maltaisn.notes.ui.settings.SettingsViewModel
import com.maltaisn.notes.ui.sync.SyncViewModel
import com.maltaisn.notes.ui.sync.accountdelete.AccountDeleteViewModel
import com.maltaisn.notes.ui.sync.main.SyncMainViewModel
import com.maltaisn.notes.ui.sync.passwordchange.PasswordChangeViewModel
import com.maltaisn.notes.ui.sync.passwordreset.PasswordResetViewModel
import com.maltaisn.notes.ui.sync.signin.SyncSignInViewModel
import com.maltaisn.notes.ui.sync.signup.SyncSignUpViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap


@Module
abstract class ViewModelModule {

    @Binds
    abstract fun bindsViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    abstract fun bindsMainViewModel(viewModel: MainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SharedViewModel::class)
    abstract fun bindsSharedViewModel(viewModel: SharedViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(HomeViewModel::class)
    abstract fun bindsHomeViewModel(viewModel: HomeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SearchViewModel::class)
    abstract fun bindsSearchViewModel(viewModel: SearchViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EditViewModel::class)
    abstract fun bindsEditViewModel(viewModel: EditViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SyncViewModel::class)
    abstract fun bindsSyncViewModel(viewModel: SyncViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SyncMainViewModel::class)
    abstract fun bindsSyncMainViewModel(viewModel: SyncMainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SyncSignInViewModel::class)
    abstract fun bindsSyncSignInViewModel(viewModel: SyncSignInViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SyncSignUpViewModel::class)
    abstract fun bindsSyncSignUpViewModel(viewModel: SyncSignUpViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PasswordResetViewModel::class)
    abstract fun bindsPasswordResetViewModel(viewModel: PasswordResetViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PasswordChangeViewModel::class)
    abstract fun bindsPasswordChangeViewModel(viewModel: PasswordChangeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AccountDeleteViewModel::class)
    abstract fun bindsAccountDeleteViewModel(viewModel: AccountDeleteViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SettingsViewModel::class)
    abstract fun bindsSettingsViewModel(viewModel: SettingsViewModel): ViewModel
}
