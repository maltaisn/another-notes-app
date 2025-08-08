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

package com.maltaisn.notes.ui.edit.undo

import com.maltaisn.notes.ui.edit.adapter.EditCheckedHeaderItem
import com.maltaisn.notes.ui.edit.adapter.EditChipsItem
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import kotlin.random.Random

fun randomString(length: IntRange, random: Random, chars: String? = null): String {
    return (0..length.random(random)).map {
        chars?.random(random) ?: ('a' + (0..<26).random(random))
    }.joinToString("")
}

fun List<EditListItem>.copy() = mapTo(mutableListOf()) {
    when (it) {
        is EditDateItem -> it.copy()
        is EditTitleItem -> it.copy()
        is EditContentItem -> it.copy()
        is EditItemItem -> it.copy()
        is EditItemAddItem -> it
        is EditCheckedHeaderItem -> it.copy()
        is EditChipsItem -> it.copy()
    }
}

