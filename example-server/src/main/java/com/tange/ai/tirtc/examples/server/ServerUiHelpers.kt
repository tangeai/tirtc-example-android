package com.tange.ai.tirtc.examples.server

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

private const val STREAM_BUBBLE_DISPLAY_MS = 2_000L

internal data class ServerUiViews(
  val loadingView: View,
  val idleHintText: TextView,
  val togglePushButton: Button,
)

internal fun AppCompatActivity.renderServerPage(
  localMediaStarted: Boolean,
  isStreaming: Boolean,
  ui: ServerUiViews,
) {
  ui.loadingView.visibility = if (isStreaming) View.GONE else View.VISIBLE
  ui.idleHintText.visibility = if (localMediaStarted) View.GONE else View.VISIBLE
  applyPushButtonStyle(ui.togglePushButton, R.string.button_exit, true)
}

internal class ServerStreamBubblePresenter(
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
    currentView.text = activity.getString(R.string.stream_bubble_sent_text, text)
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

private fun AppCompatActivity.applyPushButtonStyle(
  button: Button,
  textRes: Int,
  enabled: Boolean,
) {
  button.text = getString(textRes)
  button.isEnabled = enabled
  val tintRes = if (enabled) R.color.demo_theme_primary else R.color.demo_action_disabled
  button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, tintRes))
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
