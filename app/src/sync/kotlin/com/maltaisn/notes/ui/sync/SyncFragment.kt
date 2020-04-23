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

package com.maltaisn.notes.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.FragmentSyncBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.ui.common.ViewModelFragment
import com.maltaisn.notes.ui.sync.accountdelete.AccountDeleteDialog
import com.maltaisn.notes.ui.sync.main.SyncMainFragment
import com.maltaisn.notes.ui.sync.passwordchange.PasswordChangeDialog
import com.maltaisn.notes.ui.sync.signin.SyncSignInFragment
import com.maltaisn.notes.ui.sync.signup.SyncSignUpFragment


class SyncFragment : ViewModelFragment(), Toolbar.OnMenuItemClickListener {

    private val viewModel: SyncViewModel by viewModels { viewModelFactory }

    private var _binding: FragmentSyncBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSyncBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        // Toolbar
        val toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener {
            view.hideKeyboard()
            navController.popBackStack()
        }

        // View pager
        val viewPager = binding.viewPager
        viewPager.adapter = SyncPageAdapter(this)
        viewPager.offscreenPageLimit = 1

        // Observers
        viewModel.currentUser.observe(viewLifecycleOwner, Observer { user ->
            val menu = toolbar.menu
            val signedIn = user != null
            menu.findItem(R.id.item_password_change).isVisible = signedIn
            menu.findItem(R.id.item_account_delete).isVisible = signedIn

            // Prevent user from swiping when user is signed in.
            viewPager.isUserInputEnabled = !signedIn
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_password_change -> PasswordChangeDialog()
                    .show(childFragmentManager, PASSWORD_CHANGE_DIALOG_TAG)
            R.id.item_account_delete -> AccountDeleteDialog()
                    .show(childFragmentManager, ACCOUNT_DELETE_DIALOG_TAG)
            else -> return false
        }
        return true
    }

    fun goToPage(page: SyncPage) {
        binding.viewPager.currentItem = page.pos
    }

    private class SyncPageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun getItemCount() = 3

        override fun createFragment(position: Int) = when (position) {
            SyncPage.MAIN.pos -> SyncMainFragment()
            SyncPage.SIGN_IN.pos -> SyncSignInFragment()
            SyncPage.SIGN_UP.pos -> SyncSignUpFragment()
            else -> error("Invalid position")
        }
    }

    companion object {
        private const val PASSWORD_CHANGE_DIALOG_TAG = "password_change_dialog"
        private const val ACCOUNT_DELETE_DIALOG_TAG = "account_delete_dialog"
    }

}
