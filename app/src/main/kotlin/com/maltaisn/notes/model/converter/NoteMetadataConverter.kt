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
import com.maltaisn.notes.model.entity.NoteMetadata
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration


/**
 * Converter used to store instances of [NoteMetadata] in the database and to serialize them.
 * When serialized, metadata JSON in itself encoded into a JSON string. This wouldn't be
 * necessary but it simplifies the server's job. Also metadata *could* eventually not be JSON.
 */
@Serializer(forClass = NoteMetadata::class)
object NoteMetadataConverter : KSerializer<NoteMetadata> {

    private val json = Json(JsonConfiguration.Stable)

    @TypeConverter
    @JvmStatic
    fun toMetadata(str: String) = json.parse(NoteMetadata.serializer(), str)

    @TypeConverter
    @JvmStatic
    fun toString(metadata: NoteMetadata) = json.stringify(NoteMetadata.serializer(), metadata)


    override val descriptor = PrimitiveDescriptor("NoteMetadata", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NoteMetadata) =
            encoder.encodeString(toString(value))

    override fun deserialize(decoder: Decoder) = toMetadata(decoder.decodeString())

}
