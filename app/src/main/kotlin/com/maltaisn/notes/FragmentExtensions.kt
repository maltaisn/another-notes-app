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

package com.maltaisn.notes

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.animation.addListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Returns whether this fragment manager contains a fragment with a [tag].
 */
operator fun FragmentManager.contains(tag: String) = this.findFragmentByTag(tag) != null

fun Fragment.switchStatusBarColor(
    @ColorInt colorFrom: Int,
    @ColorInt colorTo: Int,
    duration: Long,
    endAsTransparent: Boolean = false
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return
    }

    val anim = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)

    anim.duration = duration
    anim.addUpdateListener { animator ->
        setStatusBarColor(animator.animatedValue as Int)
    }

    if (endAsTransparent) {
        anim.addListener(onEnd = {
            // Wait 50ms before resetting the status bar color to prevent flickering, when the
            // regular toolbar isn't yet visible again.
            Executors.newSingleThreadScheduledExecutor().schedule({
                setStatusBarColor(Color.TRANSPARENT)
            }, 50, TimeUnit.MILLISECONDS)
        })
    }

    anim.start()
}

fun Fragment.setStatusBarColor(@ColorInt color: Int) {
    val window = requireActivity().window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            view.setBackgroundColor(color)
            insets
        }
    } else {
        // For Android 14 and below
        @Suppress("DEPRECATION")
        window.statusBarColor = color
    }
}
