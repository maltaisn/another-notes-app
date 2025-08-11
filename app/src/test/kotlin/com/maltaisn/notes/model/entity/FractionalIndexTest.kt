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

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FractionalIndexTest {

    @Test
    fun `basic test`() {
        assertEquals(testIndex("8100"), FractionalIndex.INITIAL.append())
        assertEquals(testIndex("820000"), testIndex("81FF").append())
        assertEquals(FractionalIndex.INITIAL, testIndex("7FFF").append())
        assertEquals(testIndex("7E0001"), testIndex("7E0000").append())

        assertEquals(testIndex("7FFF"), FractionalIndex.INITIAL.prepend())
        assertEquals(testIndex("7EFFFF"), testIndex("7F00").prepend())
        assertEquals(FractionalIndex.INITIAL, testIndex("8100").prepend())
        assertEquals(testIndex("81FF"), testIndex("820000").prepend())

        assertEquals(testIndex("8100"), testIndex("800102").append())
        assertEquals(testIndex("7FFF"), testIndex("800102").prepend())
    }

    @Test
    fun `should append`() {
        var value = testIndex("7DFFFFFF")
        repeat(2 + 512 + 131072) {
            val newValue = value.append()
            assertTrue(newValue > value)
            value = newValue
        }
        assertEquals(testIndex("83000000"), value)
    }

    @Test
    fun `should prepend`() {
        var value = testIndex("83000000")
        repeat(2 + 512 + 131072) {
            val newValue = value.prepend()
            assertTrue(newValue < value)
            value = newValue
        }
        assertEquals(testIndex("7DFFFFFF"), value)
    }

    @Test
    fun `should insert`() {
        var low = testIndex("7FFFFFFF")
        var high = testIndex("83000000")
        val rng = Random(1)
        val n = 1000
        repeat(n) {
            val middle = FractionalIndex.insert(low, high)
            assertTrue(middle > low)
            assertTrue(middle < high)
            // Should have approximatively linear growth (+1 bit per insertion)
            assertEquals((it / UByte.SIZE_BITS).toDouble(), middle.size.toDouble(), 3.0)

            if (rng.nextBoolean()) {
                low = middle
            } else {
                high = middle
            }
        }
    }

    @Test
    fun `should return the same if both are identical in insert`() {
        val index0 = FractionalIndex.INITIAL
        assertEquals(index0, FractionalIndex.insert(index0, index0))

        val index1 = testIndex("7f3344")
        assertEquals(index1, FractionalIndex.insert(index1, index1))
    }

    private fun testIndex(data: String) =
        FractionalIndex.fromBytes(data.chunked(2).map { it.toUByte(16) }.toUByteArray().asByteArray())
}