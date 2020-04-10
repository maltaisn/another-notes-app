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
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.fragment.findNavController
import androidx.viewpager.widget.ViewPager
import com.maltaisn.notes.R
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.ui.ViewModelFragment
import com.maltaisn.notes.ui.sync.main.SyncMainFragment
import com.maltaisn.notes.ui.sync.signin.SyncSignInFragment
import com.maltaisn.notes.ui.sync.signup.SyncSignUpFragment


class SyncFragment : ViewModelFragment() {

    private lateinit var viewPager: ViewPager


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_sync, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        // Toolbar
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener {
            view.hideKeyboard()
            navController.popBackStack()
        }

        // View pager
        viewPager = view.findViewById(R.id.view_pager_sync)
        viewPager.adapter = SyncPageAdapter(childFragmentManager)
    }

    fun goToPage(page: SyncPage) {
        viewPager.currentItem = page.pos
    }

    private class SyncPageAdapter(fm: FragmentManager) :
            FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getCount() = 3

        override fun getItem(position: Int) = when (position) {
            SyncPage.MAIN.pos -> SyncMainFragment()
            SyncPage.SIGN_IN.pos -> SyncSignInFragment()
            SyncPage.SIGN_UP.pos -> SyncSignUpFragment()
            else -> error("Invalid position")
        }
    }

}
