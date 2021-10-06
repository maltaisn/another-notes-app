/*
 * Copyright 2021 Nicolas Maltais
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

package com.maltaisn.notes.ui.navigation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDirections
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.sync.NavGraphMainDirections
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.home.HomeFragmentDirections
import com.maltaisn.notes.ui.navigation.adapter.NavigationAdapter
import com.maltaisn.notes.ui.navigation.adapter.NavigationDestinationItem
import com.maltaisn.notes.ui.navigation.adapter.NavigationDividerItem
import com.maltaisn.notes.ui.navigation.adapter.NavigationHeaderItem
import com.maltaisn.notes.ui.navigation.adapter.NavigationItem
import com.maltaisn.notes.ui.navigation.adapter.NavigationTopItem
import com.maltaisn.notes.ui.send
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NavigationViewModel @AssistedInject constructor(
    private val labelsRepository: LabelsRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel(), NavigationAdapter.Callback {

    private val _currentHomeDestination = savedStateHandle.getLiveData<HomeDestination>(
        KEY_HOME_DESTINATION, HomeDestination.Status(NoteStatus.ACTIVE))
    val currentHomeDestination: LiveData<HomeDestination>
        get() = _currentHomeDestination

    private val _navDirectionsEvent = MutableLiveData<Event<NavDirections>>()
    val navDirectionsEvent: LiveData<Event<NavDirections>>
        get() = _navDirectionsEvent

    private val _navigationItems = MutableLiveData<List<NavigationItem>>()
    val navigationItems: LiveData<List<NavigationItem>>
        get() = _navigationItems

    private val _drawerCloseEvent = MutableLiveData<Event<Unit>>()
    val drawerCloseEvent: LiveData<Event<Unit>>
        get() = _drawerCloseEvent

    private var listItems = listOf<NavigationItem>()
        set(value) {
            field = value
            _navigationItems.value = value
        }

    private var checkedId = 0L
        set(value) {
            field = value
            savedStateHandle[KEY_CHECKED_ID] = value
        }

    init {
        checkedId = savedStateHandle[KEY_CHECKED_ID] ?: ITEM_ID_ACTIVE
        viewModelScope.launch {
            // Navigation items are constant, except for labels.
            labelsRepository.getAllLabelsByUsage().collect { labels ->
                listItems = createListItems(labels)
            }
        }
    }

    fun selectLabel(label: Label) {
        checkedId = label.id
        val pos = listItems.indexOfFirst { it is NavigationDestinationItem
                && it.destination is HomeDestination.Labels
                && it.destination.label == label }
        if (pos != -1) {
            selectNavigationItem(listItems[pos] as NavigationDestinationItem, pos)
        } // otherwise list wasn't updated yet for new label, select on update.
    }

    private fun createListItems(labels: List<Label>) = buildList {
        this += NavigationTopItem(ITEM_ID_TOP)

        this += NavigationDestinationItem(
            id = ITEM_ID_ACTIVE,
            destination = HomeDestination.Status(NoteStatus.ACTIVE),
            iconRes = R.drawable.ic_list,
            titleRes = R.string.note_location_active,
        )
        this += NavigationDestinationItem(
            id = ITEM_ID_REMINDERS,
            destination = HomeDestination.Reminders,
            iconRes = R.drawable.ic_alarm,
            titleRes = R.string.note_reminders,
        )

        this += NavigationHeaderItem(
            id = ITEM_ID_LABELS,
            titleRes = R.string.note_location_labels,
            actionDestination = NavigationDestination.NavGraph(
                NavGraphMainDirections.actionLabel(longArrayOf())),
            actionBtnTextRes = if (labels.isEmpty()) 0 else R.string.action_manage
        )
        // Add one destination item per label
        for (label in labels) {
            this += NavigationDestinationItem(
                id = label.id,
                destination = HomeDestination.Labels(label),
                iconRes = R.drawable.ic_label_outline,
                title = label.name,
            )
        }
        this += NavigationDestinationItem(
            id = ITEM_ID_LABEL_ADD,
            destination = NavigationDestination.NavGraph(
                HomeFragmentDirections.actionHomeToLabelEdit()),
            iconRes = R.drawable.ic_plus,
            titleRes = R.string.label_create,
        )

        addDivider()

        this += NavigationDestinationItem(
            id = ITEM_ID_ARCHIVED,
            destination = HomeDestination.Status(NoteStatus.ARCHIVED),
            iconRes = R.drawable.ic_archive,
            titleRes = R.string.note_location_archived,
        )
        this += NavigationDestinationItem(
            id = ITEM_ID_DELETED,
            destination = HomeDestination.Status(NoteStatus.DELETED),
            iconRes = R.drawable.ic_delete,
            titleRes = R.string.note_location_deleted,
        )
        addDivider()

        this += NavigationDestinationItem(
            id = ITEM_ID_SETTINGS,
            destination = NavigationDestination.NavGraph(
                HomeFragmentDirections.actionHomeToSettings()),
            iconRes = R.drawable.ic_settings,
            titleRes = R.string.action_settings,
        )

        restoreSelection(this)
    }

    private fun restoreSelection(list: MutableList<NavigationItem>) {
        for ((i, item) in list.withIndex()) {
            if (item is NavigationDestinationItem && item.id == checkedId) {
                list[i] = item.copy(checked = true)
                if (item.destination is HomeDestination) {
                    // Set destination again to make sure observers have the updated destination value.
                    // For example in the case a label, it could become hidden or change name.
                    // This is also needed to select a newly created label that wasn't in the list previously.
                    _currentHomeDestination.value = item.destination
                }
                return
            }
        }

        // There's no checked destination. Only way this can happen is a checked label was
        // removed. Go back to active notes.
        checkedId = ITEM_ID_ACTIVE
        list[1] = (list[1] as NavigationDestinationItem).copy(checked = true)
        _currentHomeDestination.value = HomeDestination.Status(NoteStatus.ACTIVE)
    }

    private fun MutableList<NavigationItem>.addDivider() {
        this += NavigationDividerItem(this.size.toLong() or ITEM_ID_DIVIDER_MASK)
    }

    override fun onNavigationDestinationItemClicked(item: NavigationDestinationItem, pos: Int) {
        _drawerCloseEvent.send()
        selectNavigationItem(item, pos)
    }

    private fun selectNavigationItem(item: NavigationDestinationItem, pos: Int) {
        when (val destination = item.destination) {
            is HomeDestination -> {
                // For these destinations, only one can be set at the time
                if (item.checked) {
                    // Destination already selected, no change needed
                    return
                }
                changeListItems { items ->
                    for ((i, navItem) in items.withIndex()) {
                        if (navItem is NavigationDestinationItem && navItem.checked) {
                            items[i] = navItem.copy(checked = false)
                        }
                    }
                    items[pos] = item.copy(checked = true)
                }
                checkedId = item.id
                _currentHomeDestination.value = destination
            }
            is NavigationDestination.NavGraph -> {
                _navDirectionsEvent.send(destination.directions)
            }
        }
    }

    override fun onHeaderActionButtonClicked(item: NavigationHeaderItem) {
        require(item.actionDestination is NavigationDestination.NavGraph)
        _navDirectionsEvent.send(item.actionDestination.directions)
    }

    private inline fun changeListItems(change: (MutableList<NavigationItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    @AssistedInject.Factory
    interface Factory : AssistedSavedStateViewModelFactory<NavigationViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): NavigationViewModel
    }

    companion object {
        private const val ITEM_ID_TOP = 0L
        private const val ITEM_ID_ACTIVE = -1L
        private const val ITEM_ID_ARCHIVED = -2L
        private const val ITEM_ID_DELETED = -3L
        private const val ITEM_ID_LABELS = -4L
        private const val ITEM_ID_REMINDERS = -5L
        private const val ITEM_ID_SETTINGS = -6L
        private const val ITEM_ID_LABEL_ADD = -7L

        private const val ITEM_ID_DIVIDER_MASK = 0x100000000L

        private const val KEY_HOME_DESTINATION = "destination"
        private const val KEY_CHECKED_ID = "checkedId"
    }

}
