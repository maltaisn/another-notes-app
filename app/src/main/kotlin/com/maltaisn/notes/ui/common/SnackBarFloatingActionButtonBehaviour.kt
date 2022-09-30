package com.maltaisn.notes.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import com.google.android.material.snackbar.Snackbar
import java.lang.Float.min

// See: https://github.com/material-components/material-components-android/issues/851#issuecomment-769752413
class SnackBarFloatingActionButtonBehaviour(context: Context?, attrs: AttributeSet?) : CoordinatorLayout.Behavior<View>(context, attrs) {
    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        return dependency is Snackbar.SnackbarLayout
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        val translationY = min(
            0f,
            // Move the FAB upwards with the SnackBar
            dependency.translationY - dependency.height
                    // Adjust the spacing between the two to match the margin of the FAB
                    + dependency.marginTop - child.marginBottom
        )
        if (dependency.translationY != 0f)
            child.translationY = translationY
        return true
    }

    override fun onDependentViewRemoved(parent: CoordinatorLayout, child: View, dependency: View) {
        child.translationY = 0f
        return
    }
}