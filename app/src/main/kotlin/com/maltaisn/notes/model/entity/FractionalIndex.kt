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

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.maltaisn.notes.model.entity

import com.maltaisn.notes.debugCheck
import com.maltaisn.notes.model.converter.FractionalIndexConverter
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Implementation of "fractional indexing".
 * Basically, an over-engineered way of doing what a double could almost do already.
 *
 * Surprisingly, I haven't been able to find such a simple implementation as the one here.
 * For example, there's the insanely overcomplicated
 * [https://observablehq.com/@dgreensp/implementing-fractional-indexing], which does have a good explanation.
 *
 * This object used as a sort key has the following attributes:
 * - Logarithmic growth when prepending or appending new keys.
 * - Linear growth when inserting keys between two others.
 * - Keys can be sorted by lexicographical order.
 * - No precision limit in insertion, unlike floating point values.
 *
 * The underlying byte array is made of:
 * - Byte 0: length byte, length is N = abs(b[0] - 128)+1
 * - Byte 1..N: integer part
 * - Byte (N+1)..size: fractional part
 */
@Serializable(with = FractionalIndexConverter::class)
class FractionalIndex private constructor(private val data: UByteArray) : Comparable<FractionalIndex> {

    val bytes: ByteArray
        get() = data.toByteArray()

    val size: Int
        get() = data.size

    private val intLength: Int
        get() = integerLength(data.first())

    private val sign: Int
        get() = (data.first().toInt() - INT_MIDPOINT.toInt()).sign

    init {
        debugCheck(size >= intLength)
    }

    /**
     * Returns a fractional index with an incremented integer part and no fractional part
     */
    fun append() = integerOp(+1, sign < 0, UByte.MIN_VALUE)

    /**
     * Returns a fractional index with an decremented integer part and no fractional part
     */
    fun prepend() = integerOp(-1, sign > 0, UByte.MAX_VALUE)

    private fun integerOp(increment: Int, shrinkIf: Boolean, lowest: UByte): FractionalIndex {
        val new = data.sliceArray(0..<intLength)
        for (i in new.indices.reversed()) {
            new[i] = (new[i].toInt() + increment).toUByte()
            if (i > 0 && new[i] != lowest) {
                return FractionalIndex(new)
            }
        }
        // Overflow occurred, shrink or grow the integer part.
        return FractionalIndex(if (shrinkIf) {
            UByteArray(new.size - 1) { if (it == 0) new[0] else new[it + 1] }
        } else {
            UByteArray(new.size + 1) { if (it == 0) new[0] else if (it == 1) lowest else new[it - 1] }
        })
    }

    override fun compareTo(other: FractionalIndex): Int {
        // Lexicographical comparison
        for ((a, b) in data.zip(other.data)) {
            if (a != b) {
                return a.compareTo(b)
            }
        }
        return size.compareTo(other.size)
    }

    override fun equals(other: Any?): Boolean {
        return other === this || other is FractionalIndex && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

    override fun toString(): String {
        return data.toHexString()
    }

    companion object {
        private const val INT_MIDPOINT: UByte = 0x80u

        fun fromBytes(data: ByteArray) = FractionalIndex(data.asUByteArray())

        /**
         * Returns a fractional index between [low] and [high].
         * Either can be `null` which is the equivalent of calling append, prepend, or initial.
         */
        fun insert(low: FractionalIndex?, high: FractionalIndex?): FractionalIndex {
            when {
                low == null && high == null -> return INITIAL
                low == null -> return high!!.prepend()
                high == null -> return low.append()
                low.data.contentEquals(high.data) -> return low
            }

            // Lexicographically insert between the two byte arrays.
            val data = mutableListOf<UByte>()
            for (i in 0..maxOf(low.size, high.size)) {
                val a = low.data.getOrNull(i)?.toInt() ?: UByte.MIN_VALUE.toInt()
                val b = high.data.getOrNull(i)?.toInt() ?: (UByte.MAX_VALUE.toInt() + 1)
                if ((a - b).absoluteValue < 2) {
                    data += a.toUByte()
                } else {
                    data += ((a + b) / 2).toUByte()
                    break
                }
            }

            // If needed, complete the integer part to make it valid.
            val newIntLength = integerLength(data.first())
            while (data.size < newIntLength) {
                data += INT_MIDPOINT
            }

            return FractionalIndex(data.toUByteArray())
        }

        /**
         * The fractional index to use for the first item in a list.
         */
        val INITIAL = FractionalIndex(ubyteArrayOf(INT_MIDPOINT))

        @TestOnly
        fun nth(n: Int) = if (n < 0) {
            (0..<-n).fold(INITIAL) { index, _ -> index.prepend() }
        } else {
            (0..<n).fold(INITIAL) { index, _ -> index.append() }
        }

        private fun integerLength(firstByte: UByte) = (firstByte.toInt() - INT_MIDPOINT.toInt()).absoluteValue + 1
    }
}