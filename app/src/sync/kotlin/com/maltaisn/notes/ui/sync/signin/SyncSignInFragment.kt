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

package com.maltaisn.notes.ui.sync.signin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.maltaisn.notes.App
import com.maltaisn.notes.databinding.FragmentSyncSignInBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.ui.EventObserver
import com.maltaisn.notes.ui.sync.SyncFragment
import com.maltaisn.notes.ui.sync.SyncPage
import com.maltaisn.notes.ui.sync.passwordreset.PasswordResetDialog
import com.maltaisn.notes.ui.sync.signin.SyncSignInViewModel.FieldError
import com.maltaisn.notes.ui.viewModel
import javax.inject.Inject
import javax.inject.Provider


class SyncSignInFragment : Fragment() {

    @Inject lateinit var viewModelProvider: Provider<SyncSignInViewModel>
    private val viewModel by viewModel { viewModelProvider.get() }

    private var _binding: FragmentSyncSignInBinding? = null
    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSyncSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailLayout = binding.emailEdtLayout
        val passwordLayout = binding.passwordEdtLayout
        val emailEdt = binding.emailEdt
        val passwordEdt = binding.passwordEdt

        binding.signUpBtn.setOnClickListener {
            viewModel.goToPage(SyncPage.SIGN_UP)
        }
        binding.signInBtn.setOnClickListener {
            view.hideKeyboard()
            viewModel.signIn()
        }
        binding.passwordResetBtn.setOnClickListener {
            PasswordResetDialog.newInstance(emailEdt.text.toString())
                    .show(childFragmentManager, PASSWORD_RESET_DIALOG_TAG)
        }

        emailEdt.doAfterTextChanged {
            viewModel.onEmailEntered(it?.toString() ?: "")
        }
        passwordEdt.doAfterTextChanged {
            viewModel.onPasswordEntered(it?.toString() ?: "")
        }

        // Observers
        viewModel.changePageEvent.observe(viewLifecycleOwner, EventObserver { page ->
            (parentFragment as? SyncFragment)?.goToPage(page)
        })

        viewModel.messageEvent.observe(viewLifecycleOwner, EventObserver { messageId ->
            Snackbar.make(view, messageId, Snackbar.LENGTH_SHORT).show()
        })

        viewModel.fieldError.observe(viewLifecycleOwner, Observer { error ->
            val txvLayouts = listOf(emailLayout, passwordLayout)
            val errorLayout = when (error?.location) {
                null -> null
                FieldError.Location.EMAIL -> emailLayout
                FieldError.Location.PASSWORD -> passwordLayout
            }
            for (layout in txvLayouts) {
                if (layout === errorLayout) {
                    layout.error = getString(error!!.messageId)
                } else {
                    layout.error = null
                    layout.isErrorEnabled = false
                }
            }
        })

        viewModel.clearFieldsEvent.observe(viewLifecycleOwner, EventObserver {
            emailEdt.text = null
            passwordEdt.text = null
            emailEdt.clearFocus()
            passwordEdt.clearFocus()
        })

        viewModel.progressVisible.observe(viewLifecycleOwner, Observer { visible ->
            binding.progressBar.isInvisible = !visible
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PASSWORD_RESET_DIALOG_TAG = "password_reset_dialog"
    }

}
