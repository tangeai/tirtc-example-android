package com.tange.ai.tirtc.examples.client

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

internal data class ClientStatusViews(
  val loadingView: ProgressBar,
  val videoStatusStack: View,
  val videoDisconnectMask: View,
  val videoOverlayTitle: TextView,
  val emptyHintText: TextView,
  val togglePlayButton: Button,
)

internal fun AppCompatActivity.renderClientViewState(
  state: ClientViewState,
  playbackLoadingState: ClientPlaybackLoadingState,
  hasConnectedAtLeastOnce: Boolean,
  statusDetailOverride: String?,
  views: ClientStatusViews,
) {
  val showLoading = playbackLoadingState == ClientPlaybackLoadingState.VISIBLE
  val showStatusText = !showLoading && state != ClientViewState.CONNECTED
  views.loadingView.visibility = if (showLoading) View.VISIBLE else View.GONE
  views.videoStatusStack.visibility = if (showLoading || showStatusText) View.VISIBLE else View.GONE
  views.videoDisconnectMask.visibility =
    if (shouldShowClientDisconnectMask(state, hasConnectedAtLeastOnce)) View.VISIBLE else View.GONE
  views.videoOverlayTitle.text = buildClientOverlayTitle(this, state, hasConnectedAtLeastOnce)
  views.emptyHintText.text =
    buildClientOverlayDetail(
      this,
      state,
      hasConnectedAtLeastOnce,
      statusDetailOverride,
    )
  views.emptyHintText.visibility = if (showStatusText) View.VISIBLE else View.GONE
  views.videoOverlayTitle.visibility = if (showStatusText) View.VISIBLE else View.GONE
  views.togglePlayButton.isEnabled = state == ClientViewState.IDLE || state == ClientViewState.CONNECTED
  when (state) {
    ClientViewState.IDLE -> applyPlayButtonStyle(views.togglePlayButton, R.string.button_back_to_scan, R.color.demo_theme_primary)
    ClientViewState.CONNECTING -> applyPlayButtonStyle(views.togglePlayButton, R.string.status_connecting, R.color.demo_connect_disabled)
    ClientViewState.CONNECTED -> applyPlayButtonStyle(views.togglePlayButton, R.string.button_back_to_scan, R.color.demo_theme_primary)
  }
}

private fun AppCompatActivity.applyPlayButtonStyle(
  button: Button,
  textRes: Int,
  colorRes: Int,
) {
  button.text = getString(textRes)
  button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
}
