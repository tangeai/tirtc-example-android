package com.tange.ai.tirtc.examples.server

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.tange.ai.tirtc.RtcConnCommand
import com.tange.ai.tirtc.RtcConnCommandResponse
import com.tange.ai.tirtc.TiRtc
import com.tange.ai.tirtc.TiRtcDebugging
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal object ServerDemoDefaults {
  const val defaultServiceEntry: String = "http://ep-test-tirtc.tange365.com"
  const val defaultAudioStreamId: Int = 10
  const val defaultVideoStreamId: Int = 11
  const val defaultMessageStreamId: Int = 12
  const val defaultMaxConnections: Int = 5
}

internal object ServerDemoLogger {
  private const val TAG = "TIRTC_EXAMPLE_SERVER"

  fun info(message: String) {
    Log.i(TAG, message)
  }

  fun warn(message: String) {
    Log.w(TAG, message)
  }

  fun warn(
    message: String,
    throwable: Throwable,
  ) {
    Log.w(TAG, message, throwable)
  }
}

internal data class ServerCommandRequest(
  val commandId: Int,
  val data: ByteArray,
  val timeoutMs: Long,
)

internal object ServerFacadeConnDebug {
  private const val timeCommandId: Int = 0x1F11
  private const val timeCommandTimeoutMs: Long = 3_000L
  private const val timeRequestPayloadText = "time?"
  private val timeRequestPayloadBytes: ByteArray =
    timeRequestPayloadText.toByteArray(StandardCharsets.UTF_8)

  fun createTimeRequest(timeoutMs: Long = timeCommandTimeoutMs): ServerCommandRequest {
    return ServerCommandRequest(
      commandId = timeCommandId,
      data = timeRequestPayloadBytes.copyOf(),
      timeoutMs = timeoutMs,
    )
  }

  fun createTimeResponse(): RtcConnCommandResponse {
    return RtcConnCommandResponse().apply {
      commandId = timeCommandId
      data = formatCurrentTime().toByteArray(StandardCharsets.UTF_8)
    }
  }

  fun isTimeRequest(command: RtcConnCommand): Boolean {
    return command.commandId == timeCommandId && command.data.contentEquals(timeRequestPayloadBytes)
  }

  fun payloadToText(payload: ByteArray): String {
    return payload.toString(StandardCharsets.UTF_8).trim()
  }

  fun formatCurrentTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
  }
}

private const val LOG_UPLOAD_TIMEOUT_MS = 20_000L
private const val LOG_UPLOAD_TIMEOUT_CODE = -100

internal fun installServerLogUploadAction(
  activity: AppCompatActivity,
  toolbar: MaterialToolbar,
  menuItemId: Int,
  onStarted: () -> Unit = {},
  onSucceeded: (String) -> Unit = {},
  onFailed: (Int, String) -> Unit = { _, _ -> },
) {
  val mainHandler = Handler(Looper.getMainLooper())
  val loadingDialog = ServerLoadingDialog(activity)
  toolbar.setOnMenuItemClickListener { item ->
    if (item.itemId != menuItemId) {
      return@setOnMenuItemClickListener false
    }
    val actionItem = toolbar.menu.findItem(menuItemId) ?: return@setOnMenuItemClickListener false
    if (!actionItem.isEnabled) {
      return@setOnMenuItemClickListener true
    }
    actionItem.isEnabled = false
    loadingDialog.show()
    onStarted()
    val completed = AtomicBoolean(false)
    val timeoutMessage = activity.getString(R.string.toast_log_upload_timeout_message)
    val timeoutRunnable =
      Runnable {
        if (!completed.compareAndSet(false, true)) {
          return@Runnable
        }
        actionItem.isEnabled = true
        if (loadingDialog.isShowing) {
          loadingDialog.dismiss()
        }
        Toast.makeText(
          activity,
          activity.getString(R.string.toast_log_upload_failed, LOG_UPLOAD_TIMEOUT_CODE, timeoutMessage),
          Toast.LENGTH_SHORT,
        ).show()
        onFailed(LOG_UPLOAD_TIMEOUT_CODE, timeoutMessage)
      }
    mainHandler.postDelayed(timeoutRunnable, LOG_UPLOAD_TIMEOUT_MS)
    val startCode =
      try {
        TiRtcDebugging.uploadLogs(
          object : TiRtcDebugging.UploadLogsCallback {
            override fun onSuccess(logId: String) {
              activity.runOnUiThread {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!completed.compareAndSet(false, true)) {
                  return@runOnUiThread
                }
                actionItem.isEnabled = true
                if (loadingDialog.isShowing) {
                  loadingDialog.dismiss()
                }
                AlertDialog.Builder(activity)
                  .setTitle(R.string.dialog_log_upload_success_title)
                  .setMessage(activity.getString(R.string.dialog_log_upload_success_message, logId))
                  .setPositiveButton(android.R.string.ok, null)
                  .show()
                onSucceeded(logId)
              }
            }

            override fun onFailure(
              code: Int,
              message: String,
            ) {
              activity.runOnUiThread {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!completed.compareAndSet(false, true)) {
                  return@runOnUiThread
                }
                actionItem.isEnabled = true
                if (loadingDialog.isShowing) {
                  loadingDialog.dismiss()
                }
                Toast.makeText(
                  activity,
                  activity.getString(R.string.toast_log_upload_failed, code, message),
                  Toast.LENGTH_SHORT,
                ).show()
                onFailed(code, message)
              }
            }
          },
        )
      } catch (throwable: Throwable) {
        activity.runOnUiThread {
          mainHandler.removeCallbacks(timeoutRunnable)
          if (!completed.compareAndSet(false, true)) {
            return@runOnUiThread
          }
          actionItem.isEnabled = true
          if (loadingDialog.isShowing) {
            loadingDialog.dismiss()
          }
          val message = throwable.message ?: timeoutMessage
          Toast.makeText(
            activity,
            activity.getString(R.string.toast_log_upload_failed, LOG_UPLOAD_TIMEOUT_CODE, message),
            Toast.LENGTH_SHORT,
          ).show()
          onFailed(LOG_UPLOAD_TIMEOUT_CODE, message)
        }
        LOG_UPLOAD_TIMEOUT_CODE
      }
    if (startCode != 0) {
      mainHandler.removeCallbacks(timeoutRunnable)
      if (completed.compareAndSet(false, true)) {
        actionItem.isEnabled = true
        if (loadingDialog.isShowing) {
          loadingDialog.dismiss()
        }
        Toast.makeText(
          activity,
          activity.getString(R.string.toast_log_upload_failed, startCode, timeoutMessage),
          Toast.LENGTH_SHORT,
        ).show()
        onFailed(startCode, timeoutMessage)
      }
    }
    true
  }
}

internal fun installServerLogUploadAction(
  activity: AppCompatActivity,
  actionView: View,
  onStarted: () -> Unit = {},
  onSucceeded: (String) -> Unit = {},
  onFailed: (Int, String) -> Unit = { _, _ -> },
) {
  val mainHandler = Handler(Looper.getMainLooper())
  val loadingDialog = ServerLoadingDialog(activity)
  actionView.setOnClickListener {
    if (!actionView.isEnabled) {
      return@setOnClickListener
    }
    actionView.isEnabled = false
    loadingDialog.show()
    onStarted()
    val completed = AtomicBoolean(false)
    val timeoutMessage = activity.getString(R.string.toast_log_upload_timeout_message)
    val timeoutRunnable =
      Runnable {
        if (!completed.compareAndSet(false, true)) {
          return@Runnable
        }
        actionView.isEnabled = true
        if (loadingDialog.isShowing) {
          loadingDialog.dismiss()
        }
        Toast.makeText(
          activity,
          activity.getString(R.string.toast_log_upload_failed, LOG_UPLOAD_TIMEOUT_CODE, timeoutMessage),
          Toast.LENGTH_SHORT,
        ).show()
        onFailed(LOG_UPLOAD_TIMEOUT_CODE, timeoutMessage)
      }
    mainHandler.postDelayed(timeoutRunnable, LOG_UPLOAD_TIMEOUT_MS)
    val startCode =
      try {
        TiRtcDebugging.uploadLogs(
          object : TiRtcDebugging.UploadLogsCallback {
            override fun onSuccess(logId: String) {
              activity.runOnUiThread {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!completed.compareAndSet(false, true)) {
                  return@runOnUiThread
                }
                actionView.isEnabled = true
                if (loadingDialog.isShowing) {
                  loadingDialog.dismiss()
                }
                AlertDialog.Builder(activity)
                  .setTitle(R.string.dialog_log_upload_success_title)
                  .setMessage(activity.getString(R.string.dialog_log_upload_success_message, logId))
                  .setPositiveButton(android.R.string.ok, null)
                  .show()
                onSucceeded(logId)
              }
            }

            override fun onFailure(
              code: Int,
              message: String,
            ) {
              activity.runOnUiThread {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (!completed.compareAndSet(false, true)) {
                  return@runOnUiThread
                }
                actionView.isEnabled = true
                if (loadingDialog.isShowing) {
                  loadingDialog.dismiss()
                }
                Toast.makeText(
                  activity,
                  activity.getString(R.string.toast_log_upload_failed, code, message),
                  Toast.LENGTH_SHORT,
                ).show()
                onFailed(code, message)
              }
            }
          },
        )
      } catch (throwable: Throwable) {
        activity.runOnUiThread {
          mainHandler.removeCallbacks(timeoutRunnable)
          if (!completed.compareAndSet(false, true)) {
            return@runOnUiThread
          }
          actionView.isEnabled = true
          if (loadingDialog.isShowing) {
            loadingDialog.dismiss()
          }
          val message = throwable.message ?: timeoutMessage
          Toast.makeText(
            activity,
            activity.getString(R.string.toast_log_upload_failed, LOG_UPLOAD_TIMEOUT_CODE, message),
            Toast.LENGTH_SHORT,
          ).show()
          onFailed(LOG_UPLOAD_TIMEOUT_CODE, message)
        }
        LOG_UPLOAD_TIMEOUT_CODE
      }
    if (startCode != 0) {
      mainHandler.removeCallbacks(timeoutRunnable)
      if (completed.compareAndSet(false, true)) {
        actionView.isEnabled = true
        if (loadingDialog.isShowing) {
          loadingDialog.dismiss()
        }
        Toast.makeText(
          activity,
          activity.getString(R.string.toast_log_upload_failed, startCode, timeoutMessage),
          Toast.LENGTH_SHORT,
        ).show()
        onFailed(startCode, timeoutMessage)
      }
    }
  }
}

internal fun buildRuntimeConfig(
  endpoint: String,
  context: android.content.Context,
): TiRtc.Config {
  return TiRtc.Config.Builder(context.applicationContext)
    .setEndpoint(endpoint)
    .build()
}
