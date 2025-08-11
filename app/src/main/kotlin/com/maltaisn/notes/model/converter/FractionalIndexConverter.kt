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

package com.maltaisn.notes.model.converter

import android.util.Base64
import androidx.room.TypeConverter
import com.maltaisn.notes.model.entity.FractionalIndex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * [FractionalIndex] should have been a value class, but Room has bad support for that.
 * Also, Kotlin value class are just hit and miss.
 * In that case, an array is wrapped, but we can't override equals...
 */
object FractionalIndexConverter : KSerializer<FractionalIndex> {

    @TypeConverter
    @JvmStatic
    fun fromByteArray(data: ByteArray) = FractionalIndex.fromBytes(data)

    @TypeConverter
    @JvmStatic
    fun toByteArray(value: FractionalIndex) = value.bytes

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("FractionalIndex", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FractionalIndex) {
        encoder.encodeString(Base64.encodeToString(value.bytes, Base64.NO_PADDING).trim())
    }

    override fun deserialize(decoder: Decoder): FractionalIndex {
        return FractionalIndex.fromBytes(Base64.decode(decoder.decodeString(), Base64.NO_PADDING))
    }
}
