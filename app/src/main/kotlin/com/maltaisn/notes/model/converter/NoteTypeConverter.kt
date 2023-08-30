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
import com.maltaisn.notes.model.entity.NoteType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object NoteTypeConverter : KSerializer<NoteType> {

    @TypeConverter
    @JvmStatic
    fun toInt(type: NoteType) = type.value

    @TypeConverter
    @JvmStatic
    fun toType(value: Int) = NoteType.fromValue(value)

    override val descriptor = PrimitiveSerialDescriptor("NoteType", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: NoteType) = encoder.encodeInt(toInt(value))

    override fun deserialize(decoder: Decoder) = toType(decoder.decodeInt())
}
