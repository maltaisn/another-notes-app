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

package com.maltaisn.notes.model.converter

import androidx.room.TypeConverter
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.format.RRuleFormatter
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveDescriptor
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.Serializer

@Serializer(forClass = Recurrence::class)
object RecurrenceConverter : KSerializer<Recurrence> {

    private val rruleFormatter = RRuleFormatter()

    @TypeConverter
    @JvmStatic
    fun toRecurrence(rrule: String) = rruleFormatter.parse(rrule)

    @TypeConverter
    @JvmStatic
    fun toRRule(recurrence: Recurrence) = rruleFormatter.format(recurrence)

    override val descriptor = PrimitiveDescriptor("Recurrence", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Recurrence) =
        encoder.encodeString(toRRule(value))

    override fun deserialize(decoder: Decoder) = toRecurrence(decoder.decodeString())
}
