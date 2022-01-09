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

package com.maltaisn.notes.ui.edit.actions

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.maltaisn.notes.R
import com.maltaisn.notes.ui.edit.EditViewModel

data class EditAction(
    val visible: Boolean,
    @field:StringRes @param:StringRes val title: Int,
    @field:DrawableRes @param:DrawableRes val icon: Int,
    val showInToolbar: Boolean,
    val action: (EditViewModel) -> Unit,
)

data class EditActionsVisibility(
    val undo: Boolean = false,
    val redo: Boolean = false,
    val convertToList: Boolean = false,
    val convertToText: Boolean = false,
    val reminderAdd: Boolean = false,
    val reminderEdit: Boolean = false,
    val archive: Boolean = false,
    val unarchive: Boolean = false,
    val delete: Boolean = false,
    val restore: Boolean = false,
    val deleteForever: Boolean = false,
    val pin: Boolean = false,
    val unpin: Boolean = false,
    val share: Boolean = false,
    val copy: Boolean = false,
    val uncheckAll: Boolean = false,
    val deleteChecked: Boolean = false,
    val sortItems: Boolean = false,
) {

    fun createActions(context: Context): List<EditAction> {
        return listOf(
            EditAction(undo,
                R.string.action_undo,
                R.drawable.ic_undo,
                true,
                EditViewModel::undo),
            EditAction(redo,
                R.string.action_redo,
                R.drawable.ic_redo,
                true,
                EditViewModel::redo),
            EditAction(convertToList,
                R.string.action_convert_to_list,
                R.drawable.ic_checkbox,
                true,
                EditViewModel::toggleNoteType),
            EditAction(convertToText,
                R.string.action_convert_to_text,
                R.drawable.ic_text,
                true,
                EditViewModel::toggleNoteType),
            EditAction(reminderAdd,
                R.string.action_reminder_add,
                R.drawable.ic_alarm,
                true,
                EditViewModel::changeReminder),
            EditAction(reminderEdit,
                R.string.action_reminder_edit,
                R.drawable.ic_alarm,
                true,
                EditViewModel::changeReminder),
            EditAction(true,
                R.string.action_labels,
                R.drawable.ic_label_outline,
                true,
                EditViewModel::changeLabels),
            EditAction(archive,
                R.string.action_archive,
                R.drawable.ic_archive,
                true,
                EditViewModel::moveNoteAndExit),
            EditAction(unarchive,
                R.string.action_unarchive,
                R.drawable.ic_unarchive,
                true,
                EditViewModel::moveNoteAndExit),
            EditAction(pin,
                R.string.action_pin,
                R.drawable.ic_pin,
                true,
                EditViewModel::togglePin),
            EditAction(unpin,
                R.string.action_unpin,
                R.drawable.ic_pin_outline,
                true,
                EditViewModel::togglePin),
            EditAction(share,
                R.string.action_share,
                R.drawable.ic_share,
                true,
                EditViewModel::shareNote),
            EditAction(copy,
                R.string.action_copy,
                R.drawable.ic_copy,
                false,
                {
                    it.copyNote(context.getString(R.string.edit_copy_untitled_name),
                        context.getString(R.string.edit_copy_suffix))
                }),
            EditAction(uncheckAll,
                R.string.action_uncheck_all,
                R.drawable.ic_checkbox_multiple_off,
                false,
                EditViewModel::uncheckAllItems),
            EditAction(deleteChecked,
                R.string.action_delete_checked,
                R.drawable.ic_checkbox_multiple_delete,
                false,
                EditViewModel::deleteCheckedItems),
            EditAction(sortItems,
                R.string.action_sort_items,
                R.drawable.ic_sort_alphabetical,
                false,
                EditViewModel::sortItems),
            EditAction(delete,
                R.string.action_delete,
                R.drawable.ic_delete,
                false,
                EditViewModel::deleteNote),
            EditAction(restore,
                R.string.action_restore,
                R.drawable.ic_restore,
                true,
                EditViewModel::restoreNoteAndEdit),
            EditAction(deleteForever,
                R.string.action_delete_forever,
                R.drawable.ic_delete,
                false,
                EditViewModel::deleteNote),
        )
    }
}
