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

package com.maltaisn.notes.ui.sync.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.common.ViewModelFragment
import com.maltaisn.notes.ui.sync.SyncFragment
import com.maltaisn.notes.ui.sync.SyncPage


class SyncMainFragment : ViewModelFragment() {

    private val viewModel: SyncMainViewModel by viewModels { viewModelFactory }


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_sync_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Signed out views
        val signedOutGroup: Group = view.findViewById(R.id.group_signed_out)
        val signInBtn: Button = view.findViewById(R.id.btn_sign_in)
        val signUpBtn: Button = view.findViewById(R.id.btn_sign_up)

        signInBtn.setOnClickListener {
            viewModel.goToPage(SyncPage.SIGN_IN)
        }
        signUpBtn.setOnClickListener {
            viewModel.goToPage(SyncPage.SIGN_UP)
        }

        // Signed in views
        val signedInGroup: Group = view.findViewById(R.id.group_signed_in)
        val userTxv: TextView = view.findViewById(R.id.txv_sync_signed_in_email)
        val signOutBtn: Button = view.findViewById(R.id.btn_sign_out)
        val verificationGroup: Group = view.findViewById(R.id.group_verification)
        val resendVerificationBtn: Button = view.findViewById(R.id.btn_resend_verification)

        signOutBtn.setOnClickListener {
            viewModel.signOut()
        }
        resendVerificationBtn.setOnClickListener {
            viewModel.resendVerification()
        }

        // Observers
        viewModel.changePageEvent.observe(viewLifecycleOwner, EventObserver { page ->
            (parentFragment as? SyncFragment)?.goToPage(page)
        })

        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { messageId ->
            Snackbar.make(view, messageId, Snackbar.LENGTH_SHORT).show()
        })

        viewModel.currentUser.observe(viewLifecycleOwner, Observer { user ->
            signedInGroup.isVisible = user != null
            signedOutGroup.isVisible = user == null
            verificationGroup.isVisible = user != null && !user.isEmailVerified
            if (user != null) {
                userTxv.text = user.email!!
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkVerification()
    }

}
