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

package com.maltaisn.notes.model.converter

import androidx.room.TypeConverter
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.format.RRuleFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Can't use external serializer annotation with Recurrence class outside of app module!
// Instead @Serializable(with = ...) is used on every instance that needs to be serialized.
//@Serializer(forClass = Recurrence::class)
object RecurrenceConverter : KSerializer<Recurrence> {

    private val rruleFormatter = RRuleFormatter()

    @TypeConverter
    @JvmStatic
    fun toRecurrence(rrule: String?) = rrule?.let { rruleFormatter.parse(it) }

    @TypeConverter
    @JvmStatic
    fun toRRule(recurrence: Recurrence?) = recurrence?.let { rruleFormatter.format(it) }

    override val descriptor = PrimitiveSerialDescriptor("Recurrence", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Recurrence) =
        encoder.encodeString(toRRule(value)!!)

    override fun deserialize(decoder: Decoder) = toRecurrence(decoder.decodeString())!!
}
