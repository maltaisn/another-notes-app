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

package com.maltaisn.notes.ui.note

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.core.app.SharedElementCallback
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.google.android.material.transition.MaterialElevationScale
import com.maltaisn.notes.NavGraphMainDirections
import com.maltaisn.notes.R
import com.maltaisn.notes.databinding.FragmentNoteBinding
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.main.MainActivity
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.note.adapter.NoteAdapter
import com.maltaisn.notes.ui.note.adapter.NoteListLayoutMode
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.startSharingData
import com.maltaisn.notes.ui.utils.startSafeActionMode
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import com.google.android.material.R as RMaterial

/**
 * This fragment provides common code for home and search fragments.
 */
abstract class NoteFragment : Fragment(), ActionMode.Callback, ConfirmDialog.Callback,
    NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    val sharedViewModel: SharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var prefsManager: PrefsManager

    protected abstract val viewModel: NoteViewModel

    private var _binding: FragmentNoteBinding? = null
    protected val binding get() = _binding!!

    private var actionMode: ActionMode? = null

    protected lateinit var drawerLayout: DrawerLayout

    private var spanCount = 1
    private var hideActionMode = false

    private var layoutManager: StaggeredGridLayoutManager? = null
    private var currentHomeDestinationChanged: Boolean = false

    private var isSharedElementTransitionPlaying: Boolean = false
    private var rcvOneShotPreDrawListener: OneShotPreDrawListener? = null
    private var createdNote: View? = null
    private var createdNoteId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>?,
                sharedElements: MutableMap<String, View>?
            ) {
                isSharedElementTransitionPlaying = sharedElements != null && sharedElements.isNotEmpty()
                super.onMapSharedElements(names, sharedElements)
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        // Drawer
        val activity = requireActivity() as MainActivity
        drawerLayout = activity.drawerLayout

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = NoteAdapter(context, viewModel, prefsManager)
        val layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        this.layoutManager = layoutManager
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        // Apply padding to the bottom of the recyclerview, so that the last notes aren't covered by the FAB
        val initialPadding = resources.getDimensionPixelSize(R.dimen.notes_recyclerview_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(rcv) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            rcv.updatePadding(bottom = sysWindow.bottom + initialPadding)
            insets
        }

        val navController = findNavController()
        navController.addOnDestinationChangedListener(this)

        setupViewModelObservers(adapter, layoutManager)

        enterTransition = MaterialElevationScale(false).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
        exitTransition = MaterialElevationScale(true).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }

        // Handle Shared Element Transitions when returning to this fragment.
        if (isSharedElementTransitionPlaying) {
            rcvOneShotPreDrawListener = OneShotPreDrawListener.add(binding.recyclerView) {
                exitTransition = null
                enterTransition = null
                // Start shared element transition when the recyclerview is ready to be drawn
                startPostponedEnterTransition()
            }

            // Delay shared element transition until all views are laid out
            postponeEnterTransition()
        }
    }

    private fun noteListCommitCallback() {
        // Scroll to top of notes list, when the HomeDestination has changed
        if (currentHomeDestinationChanged) {
            if (binding.recyclerView.adapter!!.itemCount > 0) {
                binding.recyclerView.scrollToPosition(0)
                binding.recyclerView.scrollBy(0, -1)
            }
            currentHomeDestinationChanged = false
        }
    }

    private fun setupNoteItemsObserver(adapter: NoteAdapter) {
        viewModel.noteItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items, ::noteListCommitCallback)

            if (isSharedElementTransitionPlaying) {
                // Remove observer to prevent changes to the recyclerview content,
                // while a transition is playing.
                viewModel.noteItems.removeObservers(viewLifecycleOwner)
            }
        }
    }

    private fun setupViewModelObservers(adapter: NoteAdapter, layoutManager: StaggeredGridLayoutManager) {
        val navController = findNavController()

        setupNoteItemsObserver(adapter)

        viewModel.listLayoutMode.observe(viewLifecycleOwner) { mode ->
            layoutManager.spanCount = resources.getInteger(when (mode!!) {
                NoteListLayoutMode.LIST -> R.integer.note_list_layout_span_count
                NoteListLayoutMode.GRID -> R.integer.note_grid_layout_span_count
            })
            spanCount = layoutManager.spanCount
            adapter.updateForListLayoutChange()
        }

        viewModel.editItemEvent.observeEvent(viewLifecycleOwner) { (noteId, pos) ->
            exitTransition = Hold()
                .apply { duration = resources.getInteger(RMaterial.integer.material_motion_duration_medium_2).toLong() }

            val itemView: View =
                binding.recyclerView.findViewHolderForAdapterPosition(pos)!!.itemView.findViewById(R.id.card_view)

            val extras = FragmentNavigatorExtras(
                itemView to "noteContainer$noteId"
            )

            // If the selected note isn't completely in view, move it into view.
            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPositions(null).minOrNull()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPositions(null).maxOrNull()
            if (firstVisibleItem != null && lastVisibleItem != null &&
                (pos < firstVisibleItem || pos > lastVisibleItem)
            ) {
                binding.recyclerView.scrollToPosition(pos)
            }
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId), extras = extras)
        }

        viewModel.currentSelection.observe(viewLifecycleOwner) { selection ->
            updateActionModeForSelection(selection)
            updateItemsForSelection(selection)
        }

        viewModel.shareEvent.observeEvent(viewLifecycleOwner) { data ->
            startSharingData(data)
        }

        viewModel.statusChangeEvent.observeEvent(viewLifecycleOwner) { statusChange ->
            sharedViewModel.onStatusChange(statusChange)
        }

        viewModel.placeholderData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                binding.placeholderImv.setImageResource(data.iconId)
                binding.placeholderTxv.setText(data.messageId)
            } else if (binding.placeholderGroup.isVisible) {
                // Recreate layout manager to prevent an issue with weird spacing at the top of the recyclerview
                // after the placeholder has been shown.
                binding.recyclerView.layoutManager =
                    StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
            }

            binding.placeholderGroup.isVisible = data != null
        }

        viewModel.showReminderDialogEvent.observeEvent(viewLifecycleOwner) { noteIds ->
            navController.navigateSafe(NavGraphMainDirections.actionReminder(noteIds.toLongArray()))
        }

        viewModel.showLabelsFragmentEvent.observeEvent(viewLifecycleOwner) { noteIds ->
            navController.navigateSafe(NavGraphMainDirections.actionLabel(noteIds.toLongArray()))
        }

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            showDeleteConfirmDialog()
        }

        sharedViewModel.messageEvent.observeEvent(viewLifecycleOwner) { messageId ->
            Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT)
                .setGestureInsetBottomIgnored(true)
                .show()
        }
        sharedViewModel.statusChangeEvent.observeEvent(viewLifecycleOwner) { statusChange ->
            showMessageForStatusChange(statusChange)
        }
        sharedViewModel.currentHomeDestinationChangeEvent.observeEvent(viewLifecycleOwner) {
            currentHomeDestinationChanged = true
        }
        sharedViewModel.sharedElementTransitionFinishedEvent.observeEvent(viewLifecycleOwner) {
            isSharedElementTransitionPlaying = false
            // Reattach observers
            setupNoteItemsObserver(adapter)

            // Reset the transition names of the fab and the newly created note
            if (createdNote != null && createdNoteId != null) {
                binding.fab.transitionName = "createNoteTransition"
                createdNote?.transitionName = "noteContainer$createdNoteId"
            }
        }
        sharedViewModel.noteCreatedEvent.observeEvent(viewLifecycleOwner) { noteId ->
            rcvOneShotPreDrawListener?.removeListener()
            OneShotPreDrawListener.add(binding.recyclerView) {
                exitTransition = null
                enterTransition = null

                // Change the transition names, so that the shared element transition returns to
                // the newly created note item in the recyclerview instead of to the FAB.
                for (c in binding.recyclerView.children) {
                    if (c.findViewById<View>(R.id.card_view)?.transitionName == "noteContainer$noteId") {
                        binding.fab.transitionName = ""
                        c.transitionName = "createNoteTransition"

                        createdNoteId = noteId
                        createdNote = c
                        break
                    }
                }

                startPostponedEnterTransition()
            }
        }
    }

    private fun updateActionModeForSelection(selection: NoteViewModel.NoteSelection) {
        if (selection.count != 0 && actionMode == null) {
            actionMode = binding.toolbar.startSafeActionMode(this)
        } else if (selection.count == 0 && actionMode != null) {
            actionMode?.finish()
            actionMode = null
        }
    }

    private fun updateItemsForSelection(selection: NoteViewModel.NoteSelection) {
        actionMode?.let {
            it.title = NUMBER_FORMAT.format(selection.count)

            // Share and copy are only visible if there is a single note selected.
            val menu = it.menu
            val copyShareVisible = selection.count == 1 && selection.status != NoteStatus.DELETED
            menu.findItem(R.id.item_share).isVisible = copyShareVisible
            menu.findItem(R.id.item_copy).isVisible = copyShareVisible

            // Pin item
            val pinItem = menu.findItem(R.id.item_pin)
            when (selection.pinned) {
                PinnedStatus.PINNED -> {
                    pinItem.isVisible = true
                    pinItem.setIcon(R.drawable.ic_pin_outline)
                    pinItem.setTitle(R.string.action_unpin)
                }
                PinnedStatus.UNPINNED -> {
                    pinItem.isVisible = true
                    pinItem.setIcon(R.drawable.ic_pin)
                    pinItem.setTitle(R.string.action_pin)
                }
                PinnedStatus.CANT_PIN -> {
                    pinItem.isVisible = false
                }
            }

            // Reminder item
            val reminderItem = menu.findItem(R.id.item_reminder)
            reminderItem.isVisible = (selection.status != NoteStatus.DELETED)
            reminderItem.setTitle(if (selection.hasReminder) {
                R.string.action_reminder_edit
            } else {
                R.string.action_reminder_add
            })

            // Labels item
            val labelsItem = menu.findItem(R.id.item_labels)
            labelsItem.isVisible = (selection.status != NoteStatus.DELETED)

            // Update move items depending on status
            val moveItem = menu.findItem(R.id.item_move)
            val deleteItem = menu.findItem(R.id.item_delete)
            when (selection.status!!) {
                NoteStatus.ACTIVE -> {
                    moveItem.setIcon(R.drawable.ic_archive)
                    moveItem.setTitle(R.string.action_archive)
                    deleteItem.setTitle(R.string.action_delete)
                }
                NoteStatus.ARCHIVED -> {
                    moveItem.setIcon(R.drawable.ic_unarchive)
                    moveItem.setTitle(R.string.action_unarchive)
                    deleteItem.setTitle(R.string.action_delete)
                }
                NoteStatus.DELETED -> {
                    moveItem.setIcon(R.drawable.ic_restore)
                    moveItem.setTitle(R.string.action_restore)
                    deleteItem.setTitle(R.string.action_delete_forever)
                }
            }
        }
    }

    private fun showDeleteConfirmDialog() {
        ConfirmDialog.newInstance(
            title = R.string.action_delete_selection_forever,
            message = R.string.trash_delete_selected_message,
            btnPositive = R.string.action_delete
        ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
    }

    @SuppressLint("WrongConstant")
    private fun showMessageForStatusChange(statusChange: StatusChange) {
        val messageId = when (statusChange.newStatus) {
            NoteStatus.ACTIVE -> if (statusChange.oldStatus == NoteStatus.DELETED) {
                R.plurals.edit_message_move_restore
            } else {
                R.plurals.edit_message_move_unarchive
            }
            NoteStatus.ARCHIVED -> R.plurals.edit_move_archive_message
            NoteStatus.DELETED -> R.plurals.edit_message_move_delete
        }
        val count = statusChange.oldNotes.size
        val message = requireContext().resources.getQuantityString(messageId, count, count)

        Snackbar.make(requireView(), message, STATUS_CHANGE_SNACKBAR_DURATION)
            .setAction(R.string.action_undo) {
                sharedViewModel.undoStatusChange()
            }
            .setGestureInsetBottomIgnored(true)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewModel.stopUpdatingList()

        // If navigating to another fragment while there is a selection (action mode shown),
        // action mode will stay shown on top of new fragment. It has to be destroyed to hide it.
        // `actionMode.finish` calls `onDestroyActionMode`, but we don't want to clear the
        // selection, hence the `hideActionMode` flag.
        // When view is recreated, the selection observer will be fired and action mode reshown.
        hideActionMode = (actionMode != null)
        actionMode?.finish()
        _binding = null

        layoutManager = null
        rcvOneShotPreDrawListener = null
        createdNote = null
        createdNoteId = null

        findNavController().removeOnDestinationChangedListener(this)
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_pin -> viewModel.togglePin()
            R.id.item_reminder -> viewModel.createReminder()
            R.id.item_labels -> viewModel.changeLabels()
            R.id.item_move -> viewModel.moveSelectedNotes()
            R.id.item_select_all -> viewModel.selectAll()
            R.id.item_share -> viewModel.shareSelectedNote()
            R.id.item_copy -> viewModel.copySelectedNote(
                getString(R.string.edit_copy_untitled_name), getString(R.string.edit_copy_suffix))
            R.id.item_delete -> viewModel.deleteSelectedNotesPre()
            else -> return false
        }
        return true
    }

    private fun switchStatusBarColor(colorFrom: Int, colorTo: Int, duration: Long, endAsTransparent: Boolean = false) {
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)

        anim.duration = duration
        anim.addUpdateListener { animator ->
            requireActivity().window.statusBarColor = animator.animatedValue as Int
        }

        if (endAsTransparent) {
            anim.addListener(onEnd = {
                // Wait 50ms before resetting the status bar color to prevent flickering, when the
                // regular toolbar isn't yet visible again.
                Executors.newSingleThreadScheduledExecutor().schedule({
                    requireActivity().window.statusBarColor = Color.TRANSPARENT
                }, 50, TimeUnit.MILLISECONDS)
            })
        }

        anim.start()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.cab_note_selection, menu)
        if (Build.VERSION.SDK_INT >= 23) {
            switchStatusBarColor(
                (binding.toolbarLayout.background as MaterialShapeDrawable).resolvedTintColor,
                MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                resources.getInteger(RMaterial.integer.material_motion_duration_long_2).toLong()
            )
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        if (!hideActionMode) {
            viewModel.clearSelection()

            if (Build.VERSION.SDK_INT >= 23) {
                switchStatusBarColor(
                    MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                    (binding.toolbarLayout.background as MaterialShapeDrawable).resolvedTintColor,
                    resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong(),
                    true
                )
            }
        }
        hideActionMode = false
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        if (destination.id == R.id.fragment_edit) {
            // If notes are selected and action mode is shown, navigating to edit fragment
            // with reminder notification or with share action won't dismiss the action mode.
            // Must do it manually.
            viewModel.clearSelection()
        }

        val noteIdsSize = arguments?.getLongArray("noteIds")?.size
        if (destination.id == R.id.fragment_label && noteIdsSize != null && noteIdsSize > 0) {
            // Change status bar color to match label fragment
            if (Build.VERSION.SDK_INT >= 23) {
                switchStatusBarColor(
                    MaterialColors.getColor(requireView(), RMaterial.attr.colorSurfaceVariant),
                    MaterialColors.getColor(requireView(), RMaterial.attr.colorSurface),
                    resources.getInteger(RMaterial.integer.material_motion_duration_long_1).toLong() * 2,
                    true
                )
            }
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (tag == DELETE_CONFIRM_DIALOG_TAG) {
            viewModel.deleteSelectedNotes()
        }
    }

    companion object {
        private val NUMBER_FORMAT = NumberFormat.getInstance()

        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"

        private const val STATUS_CHANGE_SNACKBAR_DURATION = 7500
    }
}

