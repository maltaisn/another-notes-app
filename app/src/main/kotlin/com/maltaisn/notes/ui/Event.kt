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

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * Taken from: [https://github.com/android/architecture-samples]
 * Changes were made to allow unhandled `null` values.
 */
class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set

    /**
     * Returns the content if not handled, otherwise throws an exception.
     */
    fun requireUnhandledContent(): T {
        check(!hasBeenHandled)
        hasBeenHandled = true
        return content
    }
}

/**
 * An [Observer] for [Event]s, simplifying the pattern of checking if the [Event]'s content has
 * already been handled.
 *
 * [onEventUnhandledContent] is *only* called if the [Event]'s contents has not been handled.
 */
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        if (!value.hasBeenHandled) {
            onEventUnhandledContent(value.requireUnhandledContent())
        }
    }
}

fun <T> LiveData<Event<T>>.observeEvent(owner: LifecycleOwner, observer: (T) -> Unit) {
    this.observe(owner, EventObserver(observer))
}

fun <T> MutableLiveData<Event<T>>.send(value: T) {
    this.value = Event(value)
}

fun MutableLiveData<Event<Unit>>.send() {
    this.send(Unit)
}
