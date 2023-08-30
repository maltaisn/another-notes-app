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

package com.maltaisn.notes.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Gets the value of a [LiveData] or waits for it to have one, with a timeout.
 */
fun <T> LiveData<T>.getOrAwaitValue(time: Duration = 1.seconds): T {
    var data: T? = null
    val latch = CountDownLatch(1)
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            data = value
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }
    this.observeForever(observer)

    try {
        // Don't wait indefinitely if the LiveData is not set.
        if (!latch.await(time.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            this.removeObserver(observer)
            throw TimeoutException("LiveData value was never set.")
        }
    } finally {
        this.removeObserver(observer)
    }
    @Suppress("UNCHECKED_CAST")
    return data as T
}

/**
 * Asserts the value of a [LiveData] [Event] or waits for it to have one, with a timeout.
 */
fun <T> assertLiveDataEventSent(liveData: LiveData<Event<T>>, expected: T?) {
    val event = liveData.getOrAwaitValue()
    assertFalse(event.hasBeenHandled)
    assertEquals(event.requireUnhandledContent(), expected)
}

fun assertLiveDataEventSent(liveData: LiveData<Event<Unit>>) = assertLiveDataEventSent(liveData, Unit)
