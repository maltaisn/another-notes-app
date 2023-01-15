package com.maltaisn.notes.ui.common

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.maltaisn.notes.R

class AppBarLayoutWithPaddingBehavior(context: Context?, attrs: AttributeSet?) : AppBarLayout.Behavior(context, attrs) {

    override fun onApplyWindowInsets(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        insets: WindowInsetsCompat
    ): WindowInsetsCompat {
        // Set left and right toolbar padding to prevent buttons from overlapping with system bars in landscape mode
        val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        child.findViewById<MaterialToolbar>(R.id.toolbar).updatePadding(left = sysWindow.left, right = sysWindow.right)
        return super.onApplyWindowInsets(coordinatorLayout, child, insets)
    }
}