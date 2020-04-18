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
import kotlinx.serialization.*
import java.text.SimpleDateFormat
import java.util.*


@Serializer(forClass = Date::class)
object DateTimeConverter : KSerializer<Date> {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
    }

    @TypeConverter
    @JvmStatic
    fun toDate(str: String): Date {
        return dateFormat.parse(str)!!
    }

    @TypeConverter
    @JvmStatic
    fun toString(date: Date): String = dateFormat.format(date)


    override val descriptor = PrimitiveDescriptor("Date", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeString(toString(value))
    override fun deserialize(decoder: Decoder) = toDate(decoder.decodeString())

}
