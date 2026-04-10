package com.tange.ai.tirtc.examples.client

import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors

internal fun AppCompatActivity.configureEdgeToEdgeWithPrimaryStatusBar(topBar: View) {
  WindowCompat.setDecorFitsSystemWindows(window, false)
  val primaryColor = MaterialColors.getColor(topBar, com.google.android.material.R.attr.colorPrimary)
  applyPrimaryStatusBarStyle(primaryColor)
  ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
    val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
    view.updatePadding(top = statusBars.top)
    insets
  }
}

internal fun AppCompatActivity.applyPrimaryStatusBarStyle(
  @ColorInt color: Int,
) {
  window.statusBarColor = color
  WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = false
}
