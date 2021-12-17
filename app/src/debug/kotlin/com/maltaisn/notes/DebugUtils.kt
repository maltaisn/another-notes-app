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

package com.maltaisn.notes

import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import java.util.Date
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * This is used to generate random notes for testing debug builds.
 */
object DebugUtils {

    fun getRandomNote(status: NoteStatus): Note {
        val title = getRandomString(10..32)
        val type = NoteType.values().random()

        val content: String
        val metadata: NoteMetadata
        when (type) {
            NoteType.TEXT -> {
                content = getRandomString(32..256)
                metadata = BlankNoteMetadata
            }
            NoteType.LIST -> {
                val size = (1..10).random()
                content = buildString {
                    repeat(size) {
                        appendLine(getRandomString(16..128))
                    }
                    deleteCharAt(lastIndex)
                }
                metadata = ListNoteMetadata(List(size) { Random.nextBoolean() })
            }
        }

        val added = getRandomDate()
        val modified = getRandomDate(added)

        return Note(0, type, title, content, metadata, added, modified, status,
            if (status == NoteStatus.ACTIVE) PinnedStatus.UNPINNED else PinnedStatus.CANT_PIN, null)
    }

    private fun getRandomString(size: IntRange): String {
        val s = size.random()
        var start = (0 until LOREM_IPSUM.length - s).random()
        while (start > 0) {
            if (!LOREM_IPSUM[start].isLetter()) {
                break
            }
            start--
        }
        var end = start + s
        while (end < LOREM_IPSUM.length) {
            if (!LOREM_IPSUM[end].isLetter()) {
                break
            }
            end++
        }
        return LOREM_IPSUM.substring(start + 1, end)
            .trim().replaceFirstChar { it.titlecaseChar() }
    }

    private fun getRandomDate(min: Date? = null): Date {
        val current = System.currentTimeMillis()
        val minDate = min?.time ?: (current - 5.days.inWholeMilliseconds)
        return Date((minDate..current).random())
    }

    private val LOREM_IPSUM = """
         Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce nunc lorem, tempus quis est
         sodales, tincidunt convallis lorem. Ut blandit scelerisque urna vitae rhoncus. In nec elit
         lobortis, varius nibh vel, mollis diam. Ut efficitur at purus sed rutrum. Ut sit amet est
         condimentum, pretium urna vulputate, maximus tellus. Proin aliquam mi dui, vitae maximus ex
         tempor eu. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos
         himenaeos. Phasellus vulputate accumsan ex non aliquam. Donec leo purus, hendrerit sed
         pulvinar eget, faucibus id neque. Donec et luctus ex. Integer semper in purus et ultricies.
         Integer posuere tempor blandit. In consequat euismod tincidunt. Ut fringilla iaculis
         ultrices. Suspendisse posuere scelerisque turpis, volutpat pharetra tellus elementum ut.
         Maecenas vel vulputate eros, sit amet rhoncus nibh. Fusce interdum leo at dolor posuere
         mattis. Proin at sagittis sem. Class aptent taciti sociosqu ad litora torquent per conubia
         nostra, per inceptos himenaeos. Praesent quam felis, ullamcorper sit amet ultricies vel,
         tristique at orci. Mauris rhoncus, nibh commodo luctus congue, velit libero sollicitudin
         metus, malesuada imperdiet purus magna fermentum quam. Lorem ipsum dolor sit amet,
         consectetur adipiscing elit. Aliquam porttitor odio id nibh finibus venenatis. In hac
         habitasse platea dictumst. Pellentesque et turpis vitae sapien fermentum facilisis.
         Pellentesque aliquet ex mi, sit amet finibus erat euismod id. Fusce ac tortor nec libero
         feugiat lacinia nec vitae nibh. Praesent dictum ligula eros, sit amet sollicitudin magna
         dapibus a. 
    """.trimIndent().replace('\n', ' ')
}
