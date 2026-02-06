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

package com.maltaisn.notes.ui.edit

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Browser
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.maltaisn.notes.NavGraphMainDirections
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.FragmentEditBinding
import com.maltaisn.notes.hideKeyboard
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.setIconsLayoutDirection
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.edit.actions.EditAction
import com.maltaisn.notes.ui.edit.actions.EditActionAvailability
import com.maltaisn.notes.ui.edit.actions.EditActionsAvailability
import com.maltaisn.notes.ui.edit.adapter.EditAdapter
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.startSharingData
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.R as RMaterial

import com.maltaisn.notes.getNoteColorResource
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class EditFragment : Fragment(), Toolbar.OnMenuItemClickListener, ConfirmDialog.Callback, ColorPickerDialog.Callback {

    val viewModel: EditViewModel by hiltNavGraphViewModels(R.id.fragment_edit)
    val sharedViewModel: SharedViewModel by activityViewModels()

    private val args: EditFragmentArgs by navArgs()

    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!

    var editActions: List<EditAction> = emptyList()

    private var originalStatusBarColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedElementEnterTransition = MaterialContainerTransform(requireContext(), true).apply {
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong()
        }

        sharedElementReturnTransition = MaterialContainerTransform(requireContext(), false).apply {
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong()
        }

        // Send an event via the sharedViewModel when the transition has finished playing
        (sharedElementReturnTransition as MaterialContainerTransform).addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                sharedViewModel.sharedElementTransitionFinished()
            }
        })

        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        val noteId = args.noteId
        ViewCompat.setTransitionName(
            binding.fragmentEditLayout,
            "noteContainer$noteId"
        )
        createActionItems()
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        originalStatusBarColor = requireActivity().window.statusBarColor

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            viewModel.saveNote()
            viewModel.exit()
        }

        viewModel.start(
            args.noteId,
            args.labelId,
            args.changeReminder,
            NoteType.fromValue(args.type),
            args.title,
            args.content
        )

        // Toolbar
        binding.toolbar.apply {
            setOnMenuItemClickListener(this@EditFragment)
            setNavigationOnClickListener {
                view.hideKeyboard()
                viewModel.saveNote()
                viewModel.exit()
            }

            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                private var directionSet = false

                override fun onPreDraw(): Boolean {
                    if (!directionSet) {
                        menu.setIconsLayoutDirection(view.layoutDirection)
                        directionSet = true
                    }
                    return true
                }
            })
        }

        // Recycler view
        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = EditAdapter(context, viewModel)
        val layoutManager = EditLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager
        rcv.itemAnimator = object : DefaultItemAnimator() {
            override fun animateAppearance(
                viewHolder: RecyclerView.ViewHolder,
                preLayoutInfo: ItemHolderInfo?,
                postLayoutInfo: ItemHolderInfo
            ): Boolean {
                return if (preLayoutInfo != null && (preLayoutInfo.left != postLayoutInfo.left
                            || preLayoutInfo.top != postLayoutInfo.top)
                ) {
                    // item move, handle normally
                    super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo)
                } else {
                    // do not animate new item appearance
                    // this is mainly to avoid animating the whole list when fragment view is recreated.
                    dispatchAddFinished(viewHolder)
                    false
                }
            }
        }
        rcv.setOnTouchListener { _, event ->
            // Special case to dispatch touch events to underlaying background view to focus content view.
            // This is only done if content view is not taller than RecyclerView to avoid scrolling issues (#63).
            val contentEdt = rcv.findViewById<View>(R.id.content_edt)
            if (contentEdt != null && contentEdt.height < rcv.height) {
                binding.viewBackground.dispatchTouchEvent(event)
            }
            false
        }
        binding.viewBackground.setOnClickListener {
            // On background click, focus note content if text note.
            viewModel.focusNoteContent()
        }

        // Dynamically adjust the padding on the bottom of the RecyclerView.
        // This enables edge-to-edge functionality and also handles resizing
        // when the keyboard is opened / closed.
        val initialPadding = resources.getDimensionPixelSize(R.dimen.edit_recyclerview_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(rcv) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            rcv.updatePadding(bottom = sysWindow.bottom + initialPadding)
            insets
        }

        setupViewModelObservers(adapter)

        // Delay the shared element transition until the recyclerView is ready to be drawn
        OneShotPreDrawListener.add(binding.recyclerView) {
            // Start shared element transition
            startPostponedEnterTransition()
        }
        postponeEnterTransition()
    }

    @SuppressLint("WrongConstant")
    private fun setupViewModelObservers(adapter: EditAdapter) {
        val navController = findNavController()

        viewModel.editActionsAvailability.observe(viewLifecycleOwner, ::updateActionItemsVisibility)

        // Each observer must take care not to undo the work of another observer
        // in case an attribute of a menu item is dependant on more than one criterion.
        viewModel.editItems.observe(viewLifecycleOwner, adapter::submitList)

        viewModel.focusEvent.observeEvent(viewLifecycleOwner, adapter::setItemFocus)

        viewModel.noteCreateEvent.observeEvent(viewLifecycleOwner) { noteId ->
            sharedViewModel.noteCreated(noteId)
        }

        val restoreNoteSnackbar by lazy {
            Snackbar.make(requireView(), R.string.edit_in_trash_message,
                CANT_EDIT_SNACKBAR_DURATION)
                .setGestureInsetBottomIgnored(true)
                .setAction(R.string.action_restore) { viewModel.restoreNoteAndEdit() }
        }
        viewModel.messageEvent.observeEvent(viewLifecycleOwner) { message ->
            when (message) {
                EditMessage.BLANK_NOTE_DISCARDED -> sharedViewModel.onBlankNoteDiscarded()
                EditMessage.RESTORED_NOTE -> Snackbar.make(requireView(), resources.getQuantityText(
                    R.plurals.edit_message_move_restore, 1), Snackbar.LENGTH_SHORT)
                    .setGestureInsetBottomIgnored(true)
                    .show()

                EditMessage.CANT_EDIT_IN_TRASH -> restoreNoteSnackbar.show()
            }
        }

        viewModel.statusChangeEvent.observeEvent(viewLifecycleOwner,
            sharedViewModel::onStatusChange)

        viewModel.shareEvent.observeEvent(viewLifecycleOwner, ::startSharingData)

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            ConfirmDialog.newInstance(
                title = R.string.action_delete_forever,
                message = R.string.trash_delete_message,
                btnPositive = R.string.action_delete
            ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
        }

        viewModel.showRemoveCheckedConfirmEvent.observeEvent(viewLifecycleOwner) {
            ConfirmDialog.newInstance(
                title = R.string.edit_convert_keep_checked,
                btnPositive = R.string.action_delete,
                btnNegative = R.string.action_keep
            ).show(childFragmentManager, REMOVE_CHECKED_CONFIRM_DIALOG_TAG)
        }

        viewModel.showReminderDialogEvent.observeEvent(viewLifecycleOwner) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionReminder(longArrayOf(noteId)))
        }

        viewModel.showLabelsFragmentEvent.observeEvent(viewLifecycleOwner) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionLabel(longArrayOf(noteId)))
        }

        viewModel.showColorDialogEvent.observeEvent(viewLifecycleOwner) { color ->
            ColorPickerDialog.newInstance(color).show(childFragmentManager, ColorPickerDialog.TAG)
        }

        viewModel.currentNoteColor.observe(viewLifecycleOwner) { color ->
            val colorInt = ContextCompat.getColor(requireContext(), getNoteColorResource(color))
            binding.fragmentEditLayout.setBackgroundColor(colorInt)
            binding.contentContainer.setBackgroundColor(colorInt)
            requireActivity().window.statusBarColor = colorInt
        }

        viewModel.showLinkDialogEvent.observeEvent(viewLifecycleOwner) { linkText ->
            ConfirmDialog.newInstance(
                btnPositive = R.string.action_open,
                btnNegative = R.string.action_cancel,
                messageStr = linkText,
            ).show(childFragmentManager, OPEN_LINK_DIALOG_TAG)
        }

        viewModel.openLinkEvent.observeEvent(viewLifecycleOwner) { url ->
            val uri = url.toUri()
            val context = requireContext()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // do nothing
            }
        }

        viewModel.exitEvent.observeEvent(viewLifecycleOwner) {
            navController.popBackStack()
        }

        sharedViewModel.reminderChangeEvent.observeEvent(viewLifecycleOwner) { reminder ->
            viewModel.onReminderChange(reminder)
        }

        sharedViewModel.undoRedoEvent.observeEvent(viewLifecycleOwner) { action ->
            when (action) {
                SharedViewModel.UndoRedoAction.UNDO -> viewModel.undo()
                SharedViewModel.UndoRedoAction.REDO -> viewModel.redo()
            }
        }
    }

    private fun createActionItems() {
        editActions = EditActionsAvailability().createActions(requireContext())

        val menu = binding.toolbar.menu
        val context = requireContext()
        for (action in editActions) {
            menu.add(0, action.menuItemId, 0, action.title).apply {
                setIcon(action.icon)
                isVisible = false
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                // Explicitly set content description using the resolved string
                androidx.core.view.MenuItemCompat.setContentDescription(this, context.getString(action.title))
            }
        }
        @SuppressLint("PrivateResource")
        menu.add(0, OVERFLOW_MENU_ID, 0, androidx.appcompat.R.string.abc_action_menu_overflow_description).apply {
            setIcon(R.drawable.ic_vertical_dots)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    private fun updateActionItemsVisibility(visibility: EditActionsAvailability) {
        val context = requireContext()
        editActions = visibility.createActions(context)

        val menu = binding.toolbar.menu
        val inToolbarMax = context.resources.getInteger(R.integer.edit_actions_in_toolbar)
        val showOverflow = updateToolbarItemsForEditActions(inToolbarMax, editActions) { pos, visible, enabled ->
            val action = editActions[pos]
            val item = menu.findItem(action.menuItemId)
            item?.let {
                it.isVisible = visible
                it.isEnabled = enabled
                // Re-set content description to ensure it's on the ActionMenuItemView
                if (visible) {
                    androidx.core.view.MenuItemCompat.setContentDescription(it, context.getString(action.title))
                }
            }
        }

        // Show overflow icon only if there are hidden items
        menu.findItem(OVERFLOW_MENU_ID)?.isVisible = showOverflow

        // Set content descriptions on the actual ActionMenuItemView instances
        // Use ViewTreeObserver to ensure views are laid out
        binding.toolbar.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                setContentDescriptionsOnViews()
            }
        })
    }

    private fun setContentDescriptionsOnViews() {
        val context = requireContext()

        // Find the ActionMenuView in the toolbar
        for (i in 0 until binding.toolbar.childCount) {
            val child = binding.toolbar.getChildAt(i)
            if (child is androidx.appcompat.widget.ActionMenuView) {
                // Get list of visible actions in toolbar order
                val visibleActions = editActions.filter { action ->
                    binding.toolbar.menu.findItem(action.menuItemId)?.isVisible == true
                }

                // Set content descriptions on ActionMenuItemView children
                var actionIndex = 0
                for (j in 0 until child.childCount) {
                    val view = child.getChildAt(j)
                    if (view is androidx.appcompat.view.menu.ActionMenuItemView && actionIndex < visibleActions.size) {
                        val action = visibleActions[actionIndex]
                        view.contentDescription = context.getString(action.title)
                        actionIndex++
                    }
                }
                break
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().window.statusBarColor = originalStatusBarColor
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveNote()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        for (action in editActions) {
            if (item.itemId == action.menuItemId && action.available == EditActionAvailability.AVAILABLE) {
                action.action(viewModel)
                return true
            }
        }
        if (item.itemId == OVERFLOW_MENU_ID) {
            // Overflow item
            findNavController().navigateSafe(EditFragmentDirections.actionEditToEditActions())
        }
        return false
    }

    override fun onColorSelected(color: Int) {
        viewModel.onColorChange(color)
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        when (tag) {
            DELETE_CONFIRM_DIALOG_TAG -> viewModel.deleteNoteForeverAndExit()
            REMOVE_CHECKED_CONFIRM_DIALOG_TAG -> viewModel.convertToText(false)
            OPEN_LINK_DIALOG_TAG -> viewModel.openClickedLink()
        }
    }

    override fun onDialogNegativeButtonClicked(tag: String?) {
        if (tag == REMOVE_CHECKED_CONFIRM_DIALOG_TAG) {
            viewModel.convertToText(true)
        }
    }

    companion object {
        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
        private const val REMOVE_CHECKED_CONFIRM_DIALOG_TAG = "remove_checked_confirm_dialog"
        private const val OPEN_LINK_DIALOG_TAG = "open_link_confirm_dialog"

        private const val CANT_EDIT_SNACKBAR_DURATION = 5000
        private const val OVERFLOW_MENU_ID = -1
    }
}

fun updateToolbarItemsForEditActions(
    maxCountInToolbar: Int,
    editActions: List<EditAction>,
    callback: (pos: Int, visible: Boolean, enabled: Boolean) -> Unit
): Boolean {
    var inToolbarCount = 0
    var overflow = false
    for ((i, action) in editActions.withIndex()) {
        if (action.available != EditActionAvailability.HIDDEN) {
            if (action.showInToolbar && inToolbarCount < maxCountInToolbar) {
                callback(i, true, action.available == EditActionAvailability.AVAILABLE)
                inToolbarCount++
                continue
            }
            overflow = true
        }
        callback(i, false, false)
    }
    return overflow
}