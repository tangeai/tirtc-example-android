package com.tange.ai.tirtc.examples.client

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

private const val STREAM_BUBBLE_DISPLAY_MS = 2_000L
private const val DEFAULT_MAX_STREAM_TEXT_LENGTH = 64

internal class ClientStreamBubblePresenter(
  private val activity: AppCompatActivity,
  private val container: LinearLayout,
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var bubbleView: TextView? = null
  private var hideRunnable: Runnable? = null

  fun ensureViews() {
    if (bubbleView != null) {
      return
    }
    bubbleView =
      TextView(activity).apply {
        setTextColor(ContextCompat.getColor(activity, android.R.color.black))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = ContextCompat.getDrawable(activity, R.drawable.bg_stream_message_bubble)
        val horizontalPadding = dpToPx(activity, 14)
        val verticalPadding = dpToPx(activity, 8)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        alpha = 1f
        visibility = View.GONE
        layoutParams =
          LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      }
    container.addView(bubbleView)
  }

  fun show(text: String) {
    ensureViews()
    val currentView = bubbleView ?: return
    hideRunnable?.let(mainHandler::removeCallbacks)
    currentView.text = activity.getString(R.string.stream_bubble_text, text)
    currentView.visibility = View.VISIBLE
    hideRunnable =
      Runnable {
        currentView.text = ""
        currentView.visibility = View.GONE
        hideRunnable = null
      }
    mainHandler.postDelayed(hideRunnable!!, STREAM_BUBBLE_DISPLAY_MS)
  }

  fun clear() {
    hideRunnable?.let(mainHandler::removeCallbacks)
    hideRunnable = null
    bubbleView?.let {
      it.text = ""
      it.visibility = View.GONE
    }
  }
}

private fun dpToPx(
  context: Context,
  valueDp: Int,
): Int {
  return TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    valueDp.toFloat(),
    context.resources.displayMetrics,
  ).toInt()
}

internal fun shouldShowClientDisconnectMask(
  state: ClientViewState,
  hasConnectedAtLeastOnce: Boolean,
): Boolean {
  return when (state) {
    ClientViewState.CONNECTED -> false
    ClientViewState.IDLE -> hasConnectedAtLeastOnce
    ClientViewState.CONNECTING -> true
  }
}

internal fun buildClientOverlayTitle(
  context: Context,
  state: ClientViewState,
  hasConnectedAtLeastOnce: Boolean,
): String {
  return when (state) {
    ClientViewState.IDLE -> {
      if (hasConnectedAtLeastOnce) {
        context.getString(R.string.overlay_disconnected_title)
      } else {
        context.getString(R.string.overlay_idle_title)
      }
    }
    ClientViewState.CONNECTING -> context.getString(R.string.status_connecting)
    ClientViewState.CONNECTED -> ""
  }
}

internal fun buildClientOverlayDetail(
  context: Context,
  state: ClientViewState,
  hasConnectedAtLeastOnce: Boolean,
  statusDetailOverride: String?,
): String {
  return when (state) {
    ClientViewState.IDLE -> {
      statusDetailOverride ?: if (hasConnectedAtLeastOnce) {
        context.getString(R.string.overlay_disconnected_detail)
      } else {
        context.getString(R.string.overlay_idle_detail)
      }
    }
    ClientViewState.CONNECTING -> context.getString(R.string.overlay_connecting_detail)
    ClientViewState.CONNECTED -> ""
  }
}

internal fun sanitizeClientStreamText(
  raw: String,
  maxLength: Int = DEFAULT_MAX_STREAM_TEXT_LENGTH,
): String {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) {
    return "--"
  }
  val filtered =
    buildString(trimmed.length) {
      trimmed.forEach { ch ->
        if (ch.code in 32..126 || ch.code == 10 || ch.code == 13 || ch.code == 9) {
          append(ch)
        } else {
          append('?')
        }
      }
    }
  return if (filtered.length <= maxLength) {
    filtered
  } else {
    filtered.substring(0, maxLength)
  }
}
