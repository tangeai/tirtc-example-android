package com.tange.ai.tirtc.examples.server

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tange.ai.tirtc.RtcCameraFacing
import com.tange.ai.tirtc.RtcConnCommand
import com.tange.ai.tirtc.RtcConnCommandResponse
import com.tange.ai.tirtc.RtcLocalAudioOptions
import com.tange.ai.tirtc.RtcStreamMessage
import com.tange.ai.tirtc.TiRtc
import com.tange.ai.tirtc.TiRtcAudioInput
import com.tange.ai.tirtc.TiRtcConn
import com.tange.ai.tirtc.TiRtcConnService
import com.tange.ai.tirtc.TiRtcVideoInput
import com.tange.ai.tirtc.attachAudioInput
import com.tange.ai.tirtc.attachVideoInput
import com.tange.ai.tirtc.detachAudioInput
import com.tange.ai.tirtc.detachVideoInput
import com.tange.ai.tirtc.examples.server.databinding.ActivityServerBinding
import java.nio.charset.StandardCharsets

class ServerActivity : AppCompatActivity() {
  companion object {
    private const val EXTRA_LICENSE = "extra_license"

    fun createIntent(
      context: Context,
      license: String,
    ): Intent {
      return Intent(context, ServerActivity::class.java).putExtra(EXTRA_LICENSE, license)
    }
  }

  private lateinit var binding: ActivityServerBinding
  private lateinit var streamBubblePresenter: ServerStreamBubblePresenter
  private val mainHandler = Handler(Looper.getMainLooper())
  private var connService: TiRtcConnService? = null
  private var activeConn: TiRtcConn? = null
  private var videoInput: TiRtcVideoInput? = null
  private var audioInput: TiRtcAudioInput? = null
  private var runtimeInitialized = false
  private var localMediaStarted = false
  private var previewBound = false
  private var serviceBootstrapped = false
  private var sessionState = ServerSessionState.READY
  private var streamMessageTicker: Runnable? = null
  private var streamMessageGeneration = 0
  private lateinit var activeConfig: ServerConfig

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      val allGranted = result.entries.all { (_, granted) -> granted }
      if (!allGranted) {
        showError(getString(R.string.status_permission_denied))
        return@registerForActivityResult
      }
      startPushFlow()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val license = intent.getStringExtra(EXTRA_LICENSE).orEmpty().trim()
    if (license.isEmpty()) {
      finish()
      return
    }

    activeConfig = ServerConfig(license = license, serviceEntry = ServerDemoDefaults.defaultServiceEntry)
    binding = ActivityServerBinding.inflate(layoutInflater)
    setContentView(binding.root)
    configureEdgeToEdgeWithPrimaryStatusBar(binding.topAppBarContainer)
    streamBubblePresenter = ServerStreamBubblePresenter(this, binding.serverStreamBubbleContainer)
    binding.serverStreamBubbleContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    installServerLogUploadAction(
      activity = this,
      actionView = binding.topBarUploadAction,
      onStarted = { ServerDemoLogger.info(getString(R.string.log_upload_started)) },
      onSucceeded = { logId -> ServerDemoLogger.info(getString(R.string.log_upload_success, logId)) },
      onFailed = { code, message -> ServerDemoLogger.warn(getString(R.string.log_upload_failed, code, message)) },
    )
    renderLicenseText(activeConfig.license)
    binding.togglePushButton.setOnClickListener {
      it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      exitProcess()
    }
    binding.serverCommandGetTimeButton.setOnClickListener {
      ServerDemoLogger.info(
        "server_command_button_click enabled=${binding.serverCommandGetTimeButton.isEnabled} session=$sessionState has_conn=${activeConn != null}",
      )
      it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      sendGetTimeCommandFromPanel()
    }
    updateSessionState(ServerSessionState.READY)
    renderServerPage(localMediaStarted = false, isStreaming = false, ui = ServerUiViews(binding.loading, binding.emptyHintText, binding.togglePushButton))
    streamBubblePresenter.ensureViews()
    ServerDemoLogger.info("page_on_create license_present=true")
    ensurePermissionsThenStart()
  }

  override fun onDestroy() {
    streamBubblePresenter.clear()
    mainHandler.removeCallbacksAndMessages(null)
    stopStreamMessageTicker()
    shutdownAll()
    if (runtimeInitialized) {
      TiRtc.uninitialize()
      runtimeInitialized = false
    }
    super.onDestroy()
  }

  override fun onPause() {
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    if (!previewBound && !isFinishing && !isDestroyed) {
      bindPreviewIfPossible()
    }
  }

  private fun ensurePermissionsThenStart() {
    val needed =
      arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    if (needed.isEmpty()) {
      startPushFlow()
      return
    }
    permissionLauncher.launch(needed.toTypedArray())
  }

  private fun startPushFlow() {
    if (serviceBootstrapped || activeConn != null) {
      return
    }
    if (!initializeRuntime()) {
      return
    }
    if (!prepareLocalInputs()) {
      return
    }
    if (!startLocalMediaIfNeeded()) {
      return
    }
    renderServerPage(localMediaStarted = true, isStreaming = false, ui = ServerUiViews(binding.loading, binding.emptyHintText, binding.togglePushButton))
    if (!startConnService()) {
      return
    }
    renderServerPage(localMediaStarted = true, isStreaming = true, ui = ServerUiViews(binding.loading, binding.emptyHintText, binding.togglePushButton))
  }

  private fun stopPushFlow(updateUi: Boolean) {
    cleanupConnection(activeConn, "stop_push")
    activeConn = null
    connService?.stop()
    connService = null
    serviceBootstrapped = false
    stopStreamMessageTicker()
    streamBubblePresenter.clear()
    updateSessionState(ServerSessionState.DISCONNECTED)
    if (updateUi && !isFinishing && !isDestroyed) {
      renderServerPage(localMediaStarted = localMediaStarted, isStreaming = false, ui = ServerUiViews(binding.loading, binding.emptyHintText, binding.togglePushButton))
    }
  }

  private fun shutdownAll() {
    cleanupConnection(activeConn, "shutdown_all")
    activeConn = null
    connService?.stop()
    connService = null
    serviceBootstrapped = false
    stopStreamMessageTicker()
    streamBubblePresenter.clear()
    videoInput?.stop()
    videoInput?.detachPreview()
    videoInput?.destroy()
    videoInput = null
    audioInput?.stop()
    audioInput?.destroy()
    audioInput = null
    localMediaStarted = false
    previewBound = false
  }

  private fun initializeRuntime(): Boolean {
    if (runtimeInitialized) {
      return true
    }
    val result = TiRtc.initialize(buildRuntimeConfig(activeConfig.serviceEntry, applicationContext))
    if (result != 0) {
      showError(getString(R.string.status_runtime_failed, result, "initialize failed"))
      return false
    }
    runtimeInitialized = true
    ServerDemoLogger.info("runtime_init_ok endpoint=${activeConfig.serviceEntry}")
    return true
  }

  private fun prepareLocalInputs(): Boolean {
    if (videoInput != null && audioInput != null) {
      bindPreviewIfPossible()
      return true
    }
    val createdVideoInput =
      try {
        TiRtcVideoInput()
      } catch (throwable: Throwable) {
        ServerDemoLogger.warn("video_input_create_failed", throwable)
        showError(getString(R.string.status_error, "video input create failed: ${throwable.message ?: "unknown"}"))
        return false
      }
    val createdAudioInput =
      try {
        TiRtcAudioInput()
      } catch (throwable: Throwable) {
        createdVideoInput.destroy()
        ServerDemoLogger.warn("audio_input_create_failed", throwable)
        showError(getString(R.string.status_error, "audio input create failed: ${throwable.message ?: "unknown"}"))
        return false
      }
    videoInput = createdVideoInput
    audioInput = createdAudioInput
    val videoOptionsResult =
      createdVideoInput.setOptions(
        TiRtcVideoInput.Options().apply {
          width = DEFAULT_VIDEO_WIDTH
          height = DEFAULT_VIDEO_HEIGHT
          fps = DEFAULT_VIDEO_FPS
          bitrateKbps = DEFAULT_VIDEO_BITRATE_KBPS
          cameraFacing = RtcCameraFacing.BACK
        },
      )
    if (videoOptionsResult != 0) {
      showError(getString(R.string.status_error, "video options failed code=$videoOptionsResult"))
      return false
    }
    val audioOptionsResult =
      createdAudioInput.setOptions(
        RtcLocalAudioOptions().apply {
          sampleRate = 8000
        },
      )
    if (audioOptionsResult != 0) {
      showError(getString(R.string.status_error, "audio options failed code=$audioOptionsResult"))
      return false
    }
    ServerDemoLogger.info("local_io_ready")
    bindPreviewIfPossible()
    return true
  }

  private fun bindPreviewIfPossible() {
    if (previewBound) {
      return
    }
    val input = videoInput ?: return
    val attachCode = input.attachPreview(binding.localPreview)
    if (attachCode != 0) {
      ServerDemoLogger.warn("preview_attach_failed code=$attachCode")
      return
    }
    previewBound = true
    ServerDemoLogger.info("preview_bound_ok")
  }

  private fun startLocalMediaIfNeeded(): Boolean {
    if (localMediaStarted) {
      return true
    }
    val localVideo = videoInput ?: return false
    val localAudio = audioInput ?: return false
    bindPreviewIfPossible()
    val videoStart = localVideo.start()
    if (videoStart != 0) {
      showError(getString(R.string.status_error, "video start failed code=$videoStart"))
      return false
    }
    val audioStart = localAudio.start()
    if (audioStart != 0) {
      showError(getString(R.string.status_error, "audio start failed code=$audioStart"))
      return false
    }
    localMediaStarted = true
    ServerDemoLogger.info("capture_pipeline_ready")
    return true
  }

  private fun startConnService(): Boolean {
    if (serviceBootstrapped) {
      return true
    }
    val service =
      TiRtcConnService(
        TiRtcConnService.Config(
          license = activeConfig.license,
          maxConnections = ServerDemoDefaults.defaultMaxConnections,
          mediaSendPolicy = TiRtcConnService.MEDIA_SEND_POLICY_AUTO_ON_CONNECTED,
        ),
        object : TiRtcConnService.Observer {
          override fun onStarted() {
            ServerDemoLogger.info("conn_service_started")
          }

          override fun onStopped() {
            ServerDemoLogger.info("conn_service_stopped")
          }

          override fun onConnected(connection: TiRtcConn) {
            activeConn?.takeIf { it !== connection }?.let { cleanupConnection(it, "replace_active_conn") }
            activeConn = connection
            bindAcceptedConn(connection)
          }

          override fun onError(
            code: Int,
            message: String,
          ) {
            showError(getString(R.string.status_service_start_failed, code, message))
            stopPushFlow(updateUi = true)
          }
        },
      )
    val startCode = service.start()
    if (startCode != 0) {
      showError(getString(R.string.status_service_start_failed, startCode, "start failed"))
      return false
    }
    connService = service
    serviceBootstrapped = true
    updateSessionState(ServerSessionState.CONNECTING)
    return true
  }

  private fun bindAcceptedConn(conn: TiRtcConn) {
    conn.setObserver(
      object : TiRtcConn.Observer {
        override fun onStateChanged(state: TiRtcConn.State) {
          ServerDemoLogger.info("conn_state_changed state=$state")
          when (state) {
            TiRtcConn.State.CONNECTING -> updateSessionState(ServerSessionState.CONNECTING)
            TiRtcConn.State.CONNECTED -> updateSessionState(ServerSessionState.CONNECTED)
            TiRtcConn.State.DISCONNECTED -> updateSessionState(ServerSessionState.DISCONNECTED)
            TiRtcConn.State.IDLE -> Unit
          }
        }

        override fun onDisconnected(errorCode: Int) {
          ServerDemoLogger.warn("conn_disconnected error=$errorCode")
          if (activeConn === conn) {
            activeConn = null
          }
          stopStreamMessageTicker()
          streamBubblePresenter.clear()
          updateSessionState(ServerSessionState.DISCONNECTED)
        }

        override fun onRemoteCommandRequest(
          remoteRequestId: Long,
          command: RtcConnCommand,
        ) {
          if (!ServerFacadeConnDebug.isTimeRequest(command)) {
            ServerDemoLogger.warn("remote_command_unknown request_id=$remoteRequestId command_id=${command.commandId}")
            return
          }
          showToast(R.string.toast_received_predefined_command)
          val response = ServerFacadeConnDebug.createTimeResponse()
          val replyCode = conn.replyRemoteCommand(remoteRequestId, response.commandId, response.data)
          if (replyCode == 0) {
            ServerDemoLogger.info("server_command_reply_success request_id=$remoteRequestId")
          } else {
            ServerDemoLogger.warn("server_command_reply_fail request_id=$remoteRequestId code=$replyCode")
          }
        }

        override fun onStreamMessageReceived(
          streamId: Int,
          message: RtcStreamMessage,
        ) {
          ServerDemoLogger.info("stream_message stream=$streamId length=${message.data.size}")
        }

        override fun onError(
          code: Int,
          message: String,
        ) {
          ServerDemoLogger.warn("conn_error code=$code msg=$message")
        }
      },
    )
    val attachVideo = videoInput?.let { conn.attachVideoInput(ServerDemoDefaults.defaultVideoStreamId, it) }
    val attachAudio = audioInput?.let { conn.attachAudioInput(ServerDemoDefaults.defaultAudioStreamId, it) }
    val videoOk = attachVideo == 0
    val audioOk = attachAudio == 0
    ServerDemoLogger.info("accepted_conn_bound video_ok=$videoOk audio_ok=$audioOk")
    updateSessionState(ServerSessionState.CONNECTED)
    startStreamMessageTicker(conn)
  }

  private fun cleanupConnection(
    conn: TiRtcConn?,
    source: String,
  ) {
    if (conn == null) {
      return
    }
    stopStreamMessageTicker()
    conn.detachVideoInput(ServerDemoDefaults.defaultVideoStreamId)
    conn.detachAudioInput(ServerDemoDefaults.defaultAudioStreamId)
    conn.destroy()
    ServerDemoLogger.info("cleanup_connection source=$source")
  }

  private fun sendGetTimeCommandFromPanel() {
    val conn = activeConn
    if (conn == null || sessionState != ServerSessionState.CONNECTED) {
      showToast(R.string.toast_command_requires_connection)
      return
    }
    val request = ServerFacadeConnDebug.createTimeRequest()
    val result =
      conn.requestCommand(
        request.commandId,
        request.data,
        request.timeoutMs,
        object : TiRtcConn.CommandCallback {
          override fun onSuccess(response: RtcConnCommandResponse) {
            mainHandler.post {
              val text = ServerFacadeConnDebug.payloadToText(response.data)
              showToast(getString(R.string.toast_received_command_reply, text))
            }
          }

          override fun onFailure(
            code: Int,
            message: String,
          ) {
            mainHandler.post {
              ServerDemoLogger.warn("server_command_reply_fail manual=true code=$code msg=$message")
              showToast(R.string.toast_command_failed)
            }
          }
        },
      )
    if (result != 0) {
      ServerDemoLogger.warn("server_command_request_fail manual=true code=$result")
      showToast(R.string.toast_command_failed)
    }
  }

  private fun startStreamMessageTicker(conn: TiRtcConn) {
    stopStreamMessageTicker()
    streamMessageGeneration += 1
    val generation = streamMessageGeneration
    val task =
      object : Runnable {
        override fun run() {
          if (streamMessageGeneration != generation) {
            return
          }
          if (activeConn !== conn || sessionState != ServerSessionState.CONNECTED) {
            ServerDemoLogger.warn("server_stream_message_send_after_disconnect")
            return
          }
          val text = ServerFacadeConnDebug.formatCurrentTime()
          val payload = text.toByteArray(StandardCharsets.UTF_8)
          val sendResult =
            conn.sendStreamMessage(
              ServerDemoDefaults.defaultMessageStreamId,
              0L,
              payload,
            )
          if (sendResult == 0) {
            ServerDemoLogger.info("server_stream_message_send stream_id=${ServerDemoDefaults.defaultMessageStreamId} text=$text")
            streamBubblePresenter.show(text)
          } else {
            ServerDemoLogger.warn("server_stream_message_send_fail code=$sendResult msg=send failed")
          }
          if (activeConn === conn) {
            mainHandler.postDelayed(this, STREAM_MESSAGE_INTERVAL_MS)
          }
        }
      }
    streamMessageTicker = task
    mainHandler.postDelayed(task, STREAM_MESSAGE_INITIAL_DELAY_MS)
  }

  private fun stopStreamMessageTicker() {
    streamMessageTicker?.let(mainHandler::removeCallbacks)
    streamMessageTicker = null
    streamMessageGeneration += 1
    streamBubblePresenter.clear()
  }

  private fun updateSessionState(next: ServerSessionState) {
    sessionState = next
    binding.serverCommandGetTimeButton.isEnabled = next == ServerSessionState.CONNECTED
  }

  private fun renderLicenseText(license: String) {
    binding.peerIdText.text = getString(R.string.label_license_value, license)
  }

  private fun showError(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    ServerDemoLogger.warn("state_error msg=$message")
  }

  private fun showToast(messageRes: Int) {
    showToast(getString(messageRes))
  }

  private fun showToast(message: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    } else {
      mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
  }

  private fun exitProcess() {
    finishAffinity()
    Process.killProcess(Process.myPid())
  }
}
