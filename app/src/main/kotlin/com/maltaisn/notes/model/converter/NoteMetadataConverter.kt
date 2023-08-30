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
import com.maltaisn.notes.model.BadDataException
import com.maltaisn.notes.model.entity.NoteMetadata
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Converter used to store instances of [NoteMetadata] in the database and to serialize them.
 * When serialized, metadata JSON in itself encoded into a JSON string. This wouldn't be
 * necessary but it simplifies the server's job. Also metadata *could* eventually not be JSON.
 */
object NoteMetadataConverter : KSerializer<NoteMetadata> {

    private val json = Json

    @TypeConverter
    @JvmStatic
    fun toMetadata(str: String) = try {
        json.decodeFromString(NoteMetadata.serializer(), str)
    } catch (e: SerializationException) {
        throw BadDataException(cause = e)
    }

    @TypeConverter
    @JvmStatic
    fun toString(metadata: NoteMetadata) = json.encodeToString(NoteMetadata.serializer(), metadata)

    override val descriptor = PrimitiveSerialDescriptor("NoteMetadata", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NoteMetadata) =
        encoder.encodeString(toString(value))

    override fun deserialize(decoder: Decoder) = toMetadata(decoder.decodeString())
}
