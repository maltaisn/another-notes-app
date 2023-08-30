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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeConverter : KSerializer<Date> {

    private val threadLocalDateFormat = ThreadLocal<SimpleDateFormat>()

    val dateFormat: SimpleDateFormat
        get() {
            // SimpleDateFormat is not thread-safe therefore a ThreadLocal is used here. It's only
            // used for serialization so it technically doesn't have to be thread-safe, but whatever.
            var dateFormat = threadLocalDateFormat.get()
            if (dateFormat == null) {
                dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
                dateFormat.timeZone = TimeZone.getTimeZone("GMT")
                threadLocalDateFormat.set(dateFormat)
            }
            return dateFormat
        }

    @TypeConverter
    @JvmStatic
    fun toDate(date: Long) = Date(date)

    @TypeConverter
    @JvmStatic
    fun toLong(date: Date) = date.time

    override val descriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Date) =
        encoder.encodeString(dateFormat.format(value))

    override fun deserialize(decoder: Decoder) = dateFormat.parse(decoder.decodeString())!!
}
