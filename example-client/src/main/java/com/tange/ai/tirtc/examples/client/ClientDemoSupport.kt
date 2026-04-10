package com.tange.ai.tirtc.examples.client

import android.content.Context
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
import com.tange.ai.tirtc.internal.ErrorCodes
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal object ClientDemoDefaults {
  const val defaultServiceEntry: String = "http://ep-test-tirtc.tange365.com"
  const val defaultClientLocalId: String = "android-example-client"
  const val defaultAudioStreamId: Int = 10
  const val defaultVideoStreamId: Int = 11
}

internal object ClientRuntimeBootstrap {
  private val lock = Any()

  @Volatile private var initializedEndpoint: String? = null

  fun initializeForApplication(context: Context): Int {
    return ensureInitialized(context.applicationContext, ClientDemoDefaults.defaultServiceEntry)
  }

  fun ensureInitialized(
    context: Context,
    endpoint: String,
  ): Int {
    val normalizedEndpoint = endpoint.trim().ifEmpty { ClientDemoDefaults.defaultServiceEntry }
    synchronized(lock) {
      if (initializedEndpoint == normalizedEndpoint) {
        return ErrorCodes.OK
      }
      if (initializedEndpoint != null) {
        TiRtc.uninitialize()
        initializedEndpoint = null
      }
      val code =
        TiRtc.initialize(
          TiRtc.Config.Builder(context.applicationContext)
            .setEndpoint(normalizedEndpoint)
            .build(),
        )
      if (code == ErrorCodes.OK) {
        initializedEndpoint = normalizedEndpoint
      }
      return code
    }
  }
}

internal object ClientDemoLogger {
  private const val TAG = "TIRTC_EXAMPLE_CLIENT"

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

internal data class ClientCommandRequest(
  val commandId: Int,
  val data: ByteArray,
  val timeoutMs: Long,
)

internal data class ClientSessionConfig(
  val peerId: String,
  val localId: String,
  val serviceEntry: String,
  val token: String,
)

internal fun AppCompatActivity.buildClientTransportErrorMessage(code: Int): String? {
  return when (code) {
    ErrorCodes.TRANSPORT_TOKEN_EXPIRED -> getString(R.string.status_error_token_expired)
    ErrorCodes.TRANSPORT_INVALID_LICENSE -> getString(R.string.status_error_transport_invalid_license)
    ErrorCodes.TRANSPORT_TIMEOUT -> getString(R.string.status_error_transport_timeout)
    ErrorCodes.TRANSPORT_BUSY -> getString(R.string.status_error_transport_busy)
    ErrorCodes.TRANSPORT_CONNECTION_TIMEOUT_CLOSED -> getString(R.string.status_error_transport_connection_timeout_closed)
    ErrorCodes.TRANSPORT_REMOTE_CLOSED -> getString(R.string.status_error_transport_remote_closed)
    ErrorCodes.TRANSPORT_CONNECTION_OTHER_ERROR -> getString(R.string.status_error_transport_connection_other)
    else -> null
  }
}

internal object ClientFacadeConnDebug {
  private const val timeCommandId: Int = 0x1F11
  const val getTimeCommandName: String = "发送预定义命令"
  private const val timeCommandTimeoutMs: Long = 3_000L
  private const val timeRequestPayloadText = "time?"
  private val timeRequestPayloadBytes: ByteArray =
    timeRequestPayloadText.toByteArray(StandardCharsets.UTF_8)

  fun createTimeRequest(timeoutMs: Long = timeCommandTimeoutMs): ClientCommandRequest {
    return ClientCommandRequest(
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

  private fun formatCurrentTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
  }
}

private const val LOG_UPLOAD_TIMEOUT_MS = 20_000L
private const val LOG_UPLOAD_TIMEOUT_CODE = -100

internal fun installClientLogUploadAction(
  activity: AppCompatActivity,
  toolbar: MaterialToolbar,
  menuItemId: Int,
  onStarted: () -> Unit = {},
  onSucceeded: (String) -> Unit = {},
  onFailed: (Int, String) -> Unit = { _, _ -> },
) {
  val mainHandler = Handler(Looper.getMainLooper())
  val loadingDialog = ClientLoadingDialog(activity)
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

internal fun installClientLogUploadAction(
  activity: AppCompatActivity,
  actionView: View,
  onStarted: () -> Unit = {},
  onSucceeded: (String) -> Unit = {},
  onFailed: (Int, String) -> Unit = { _, _ -> },
) {
  val mainHandler = Handler(Looper.getMainLooper())
  val loadingDialog = ClientLoadingDialog(activity)
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
