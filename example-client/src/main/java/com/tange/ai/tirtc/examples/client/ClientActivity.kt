package com.tange.ai.tirtc.examples.client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tange.ai.tirtc.RtcConnCommand
import com.tange.ai.tirtc.RtcConnCommandResponse
import com.tange.ai.tirtc.RtcStreamMessage
import com.tange.ai.tirtc.TiRtcAudioOutput
import com.tange.ai.tirtc.TiRtcConn
import com.tange.ai.tirtc.TiRtcVideoOutput
import com.tange.ai.tirtc.attachAudioOutput
import com.tange.ai.tirtc.attachVideoOutput
import com.tange.ai.tirtc.detachAudioOutput
import com.tange.ai.tirtc.detachVideoOutput
import com.tange.ai.tirtc.examples.client.databinding.ActivityClientBinding
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class ClientActivity : AppCompatActivity() {
  companion object {
    private const val EXTRA_PEER_ID = "extra_peer_id"
    private const val EXTRA_LOCAL_ID = "extra_local_id"
    private const val EXTRA_SERVICE_ENTRY = "extra_service_entry"
    private const val EXTRA_TOKEN = "extra_token"

    internal fun createIntent(
      context: Context,
      payload: ScanPayload,
    ): Intent {
      return Intent(context, ClientActivity::class.java)
        .putExtra(EXTRA_PEER_ID, payload.peerId)
        .putExtra(EXTRA_LOCAL_ID, payload.localId)
        .putExtra(EXTRA_SERVICE_ENTRY, payload.serviceEntry)
        .putExtra(EXTRA_TOKEN, payload.token)
    }
  }

  private lateinit var binding: ActivityClientBinding
  private lateinit var streamBubblePresenter: ClientStreamBubblePresenter
  private val mainHandler = Handler(Looper.getMainLooper())
  private val sdkIoExecutor = Executors.newSingleThreadExecutor()
  private val pageInstanceId: Long = System.currentTimeMillis()

  private var conn: TiRtcConn? = null
  private var videoOutput: TiRtcVideoOutput? = null
  private var audioOutput: TiRtcAudioOutput? = null
  private var viewState = ClientViewState.IDLE
  private var playbackLoadingState = ClientPlaybackLoadingState.HIDDEN
  private var hasConnectedAtLeastOnce = false
  private var statusDetailOverride: String? = null
  private var sessionState = ClientSessionState.READY
  private var shouldReturnToScannerOnResume = false
  private var navigatingToScanner = false
  private lateinit var activeConfig: ClientSessionConfig

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val peerId = intent.getStringExtra(EXTRA_PEER_ID).orEmpty().trim()
    val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty().trim()
    if (peerId.isEmpty() || token.isEmpty()) {
      finish()
      return
    }

    activeConfig =
      ClientSessionConfig(
        peerId = peerId,
        localId = intent.getStringExtra(EXTRA_LOCAL_ID).orEmpty().trim().ifEmpty { ClientDemoDefaults.defaultClientLocalId },
        serviceEntry = intent.getStringExtra(EXTRA_SERVICE_ENTRY).orEmpty().trim().ifEmpty { ClientDemoDefaults.defaultServiceEntry },
        token = token,
      )
    binding = ActivityClientBinding.inflate(layoutInflater)
    setContentView(binding.root)
    configureEdgeToEdgeWithPrimaryStatusBar(binding.topAppBarContainer)
    setSupportActionBar(binding.topBar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    ClientDemoLogger.info("page_on_create instance=$pageInstanceId peer_id=${activeConfig.peerId}")
    streamBubblePresenter = ClientStreamBubblePresenter(this, binding.clientStreamBubbleContainer)
    binding.clientStreamBubbleContainer.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    installClientLogUploadAction(
      activity = this,
      actionView = binding.topBarUploadAction,
      onStarted = { ClientDemoLogger.info(getString(R.string.log_upload_started)) },
      onSucceeded = { logId -> ClientDemoLogger.info(getString(R.string.log_upload_success, logId)) },
      onFailed = { code, message -> ClientDemoLogger.warn(getString(R.string.log_upload_failed, code, message)) },
    )
    updatePeerIdText()
    binding.togglePlayButton.setOnClickListener {
      it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      when (viewState) {
        ClientViewState.IDLE -> returnToScanner("idle_button")
        ClientViewState.CONNECTED -> returnToScanner("manual_stop")
        ClientViewState.CONNECTING -> Unit
      }
    }
    binding.clientCommandGetTimeButton.setOnClickListener {
      it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      sendGetTimeFromPanel()
    }
    updateSessionState(ClientSessionState.READY)
    updateViewState(ClientViewState.IDLE)
    streamBubblePresenter.ensureViews()
    prepareConnectionIfNeeded()
  }

  override fun onDestroy() {
    ClientDemoLogger.info("page_on_destroy instance=$pageInstanceId")
    streamBubblePresenter.clear()
    mainHandler.removeCallbacksAndMessages(null)
    teardownConnectionOnly("page_destroy")
    sdkIoExecutor.shutdown()
    super.onDestroy()
  }

  override fun onPause() {
    super.onPause()
    if (isChangingConfigurations) {
      return
    }
    if (viewState == ClientViewState.CONNECTING || viewState == ClientViewState.CONNECTED) {
      ClientDemoLogger.info("lifecycle_pause_mark_rescan state=$viewState instance=$pageInstanceId")
      shouldReturnToScannerOnResume = true
      stopPullFlow("lifecycle_pause", showManualStoppedDetail = false)
    }
  }

  override fun onResume() {
    super.onResume()
    if (shouldReturnToScannerOnResume && !navigatingToScanner && !isFinishing && !isDestroyed) {
      shouldReturnToScannerOnResume = false
      returnToScanner("lifecycle_resume_after_background")
      return
    }
    if (viewState == ClientViewState.IDLE && sessionState == ClientSessionState.READY) {
      connectNow()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      returnToScanner("toolbar_back")
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onBackPressed() {
    returnToScanner("system_back")
  }

  private fun updatePeerIdText() {
    binding.pridText.text = getString(R.string.label_peer_id_value, activeConfig.peerId)
  }

  private fun connectNow() {
    statusDetailOverride = null
    if (!initializeRuntimeIfNeeded(activeConfig)) {
      return
    }
    teardownConnectionOnly("connect_now")

    if (!prepareConnectionIfNeeded()) {
      return
    }

    val currentConn = conn ?: return
    updateSessionState(ClientSessionState.CONNECTING)
    updateViewState(ClientViewState.CONNECTING)
    runOnIo("connect_now") {
      connectNowOnIo(activeConfig, currentConn)
    }
  }

  private fun prepareConnectionIfNeeded(): Boolean {
    if (conn != null && videoOutput != null && audioOutput != null) {
      return true
    }

    val createdVideoOutput =
      try {
        TiRtcVideoOutput()
      } catch (throwable: Throwable) {
        ClientDemoLogger.warn("video_output_create_failed", throwable)
        showError(getString(R.string.status_error, "video output create failed: ${throwable.message ?: "unknown"}"))
        return false
      }
    val createdAudioOutput =
      try {
        TiRtcAudioOutput()
      } catch (throwable: Throwable) {
        createdVideoOutput.destroy()
        ClientDemoLogger.warn("audio_output_create_failed", throwable)
        showError(getString(R.string.status_error, "audio output create failed: ${throwable.message ?: "unknown"}"))
        return false
      }
    val attachCode = createdVideoOutput.attach(binding.remoteVideoView)
    if (attachCode != 0) {
      createdVideoOutput.destroy()
      createdAudioOutput.destroy()
      showError(getString(R.string.status_error, "video output attach failed code=$attachCode"))
      return false
    }

    val createdConn = createConnection(createdVideoOutput, createdAudioOutput) ?: run {
      clearCurrentOutputs(createdVideoOutput, createdAudioOutput)
      return false
    }

    videoOutput = createdVideoOutput
    audioOutput = createdAudioOutput
    conn = createdConn
    setPlaybackLoadingState(ClientPlaybackLoadingState.VISIBLE)
    createdVideoOutput.setObserver(
      object : TiRtcVideoOutput.Observer {
        override fun onStateChanged(state: TiRtcVideoOutput.State) {
          ClientDemoLogger.info("video_output_state_changed state=$state instance=$pageInstanceId")
          when (state) {
            TiRtcVideoOutput.State.BUFFERING -> setPlaybackLoadingState(ClientPlaybackLoadingState.VISIBLE)
            TiRtcVideoOutput.State.RENDERING -> setPlaybackLoadingState(ClientPlaybackLoadingState.HIDDEN)
            TiRtcVideoOutput.State.IDLE,
            TiRtcVideoOutput.State.PAUSED,
            TiRtcVideoOutput.State.COMPLETED,
            TiRtcVideoOutput.State.FAILED,
            -> Unit
          }
        }

        override fun onOutputSizeChanged(
          width: Int,
          height: Int,
        ) = Unit

        override fun onError(
          code: Int,
          message: String,
        ) {
          ClientDemoLogger.warn("video_output_error code=$code msg=$message instance=$pageInstanceId")
        }
      },
    )
    val attachVideoCode = createdConn.attachVideoOutput(ClientDemoDefaults.defaultVideoStreamId, createdVideoOutput)
    val attachAudioCode = createdConn.attachAudioOutput(ClientDemoDefaults.defaultAudioStreamId, createdAudioOutput)
    if (attachVideoCode != 0 || attachAudioCode != 0) {
      ClientDemoLogger.warn("attach_outputs_failed video_code=$attachVideoCode audio_code=$attachAudioCode instance=$pageInstanceId")
      teardownConnectionOnly("prepare_connection_attach_failed")
      showError(getString(R.string.status_error, "attach outputs failed video=$attachVideoCode audio=$attachAudioCode"))
      return false
    }
    return true
  }

  private fun connectNowOnIo(
    config: ClientSessionConfig,
    currentConn: TiRtcConn,
  ) {
    if (isFinishing || isDestroyed) {
      return
    }
    val connectCode = currentConn.connect(config.peerId, config.token)
    if (connectCode != 0) {
      showError(buildConnectFailureMessage(connectCode))
      runOnMainThread {
        teardownConnectionOnly("connect_failed")
        updateSessionState(ClientSessionState.DISCONNECTED)
      }
    }
  }

  private fun onConnected(connection: TiRtcConn) {
    statusDetailOverride = null
    hasConnectedAtLeastOnce = true
    if (conn !== connection) {
      return
    }
    updateViewState(ClientViewState.CONNECTED)
    updateSessionState(ClientSessionState.CONNECTED)
  }

  private fun sendGetTimeFromPanel() {
    val currentConn = conn
    if (currentConn == null || sessionState != ClientSessionState.CONNECTED) {
      showToast(R.string.toast_command_requires_connection)
      ClientDemoLogger.warn("manual_command_skip not_connected instance=$pageInstanceId")
      return
    }
    val request = ClientFacadeConnDebug.createTimeRequest()
    runOnIo("request_command") {
      val requestCode =
        currentConn.requestCommand(
          request.commandId,
          request.data,
          request.timeoutMs,
          object : TiRtcConn.CommandCallback {
            override fun onSuccess(response: RtcConnCommandResponse) {
              runOnMainThread {
                val text = ClientFacadeConnDebug.payloadToText(response.data)
                showToast(getString(R.string.toast_received_command_reply, text))
              }
            }

            override fun onFailure(
              code: Int,
              message: String,
            ) {
              runOnMainThread {
                ClientDemoLogger.warn("client_command_reply_fail manual=true code=$code msg=$message instance=$pageInstanceId")
                showToast(R.string.toast_command_failed)
              }
            }
          },
        )
      if (requestCode != 0) {
        runOnMainThread {
          ClientDemoLogger.warn("client_command_request_fail manual=true code=$requestCode instance=$pageInstanceId")
          showToast(R.string.toast_command_failed)
        }
      }
    }
  }

  private fun showStreamBubble(text: String) {
    runOnMainThread {
      streamBubblePresenter.show(text)
      ClientDemoLogger.info("client_stream_bubble_show text=$text instance=$pageInstanceId")
    }
  }

  private fun showToast(messageRes: Int) {
    showToast(getString(messageRes))
  }

  private fun showToast(message: String) {
    runOnMainThread {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
  }

  private fun handleConnectionBroken(message: String) {
    runOnMainThread {
      teardownConnectionOnly("connection_broken:$message")
      showError(message)
      returnToScanner("connection_broken")
    }
  }

  private fun returnToScanner(source: String) {
    if (isFinishing || isDestroyed || navigatingToScanner) {
      return
    }
    navigatingToScanner = true
    shouldReturnToScannerOnResume = false
    ClientDemoLogger.info("return_to_scanner source=$source instance=$pageInstanceId")
    stopPullFlow(source, showManualStoppedDetail = false)
    startActivity(Intent(this, ScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    finish()
  }

  private fun buildConnectFailureMessage(code: Int): String {
    return buildClientTransportErrorMessage(code) ?: getString(R.string.status_error, "connect failed code=$code")
  }

  private fun buildConnErrorMessage(
    code: Int,
    message: String,
  ): String {
    return buildClientTransportErrorMessage(code) ?: getString(R.string.status_error, message.ifBlank { "error:$code" })
  }

  private fun buildDisconnectMessage(errorCode: Int): String {
    return buildClientTransportErrorMessage(errorCode) ?: getString(R.string.status_error, "disconnected:$errorCode")
  }

  private fun teardownConnectionOnly(source: String) {
    setPlaybackLoadingState(ClientPlaybackLoadingState.HIDDEN)
    ClientDemoLogger.info("teardown_connection source=$source instance=$pageInstanceId")
    val currentConn = conn
    val currentVideoOutput = videoOutput
    val currentAudioOutput = audioOutput
    conn = null
    videoOutput = null
    audioOutput = null
    runOnIo("teardown_connection") {
      currentConn?.detachVideoOutput(ClientDemoDefaults.defaultVideoStreamId)
      currentConn?.detachAudioOutput(ClientDemoDefaults.defaultAudioStreamId)
      currentConn?.disconnect()
      currentConn?.destroy()
      currentAudioOutput?.destroy()
    }
    runOnMainThread {
      currentVideoOutput?.detach()
      currentVideoOutput?.destroy()
    }
    streamBubblePresenter.clear()
  }

  private fun createConnection(
    createdVideoOutput: TiRtcVideoOutput,
    createdAudioOutput: TiRtcAudioOutput,
  ): TiRtcConn? {
    var createdConn: TiRtcConn? = null
    var callbackReady = false
    val connObserver =
      object : TiRtcConn.Observer {
        private fun isActiveConnectionCallback(): Boolean {
          return callbackReady && conn === createdConn
        }

        override fun onStateChanged(state: TiRtcConn.State) {
          if (!isActiveConnectionCallback()) {
            return
          }
          ClientDemoLogger.info("conn_state_changed state=$state instance=$pageInstanceId")
          when (state) {
            TiRtcConn.State.IDLE -> Unit
            TiRtcConn.State.CONNECTING -> {
              updateSessionState(ClientSessionState.CONNECTING)
              updateViewState(ClientViewState.CONNECTING)
            }
            TiRtcConn.State.CONNECTED -> createdConn?.let(::onConnected)
            TiRtcConn.State.DISCONNECTED -> Unit
          }
        }

        override fun onDisconnected(errorCode: Int) {
          if (!isActiveConnectionCallback()) {
            return
          }
          ClientDemoLogger.warn("conn_disconnected error=$errorCode instance=$pageInstanceId")
          updateSessionState(ClientSessionState.DISCONNECTED)
          handleConnectionBroken(buildDisconnectMessage(errorCode))
        }

        override fun onRemoteCommandRequest(
          remoteRequestId: Long,
          command: RtcConnCommand,
        ) {
          if (!isActiveConnectionCallback()) {
            return
          }
          if (!ClientFacadeConnDebug.isTimeRequest(command)) {
            ClientDemoLogger.warn("unexpected_remote_command request_id=$remoteRequestId command_id=${command.commandId} instance=$pageInstanceId")
            return
          }
          showToast(R.string.toast_received_predefined_command)
          runOnIo("reply_remote_command") {
            val response = ClientFacadeConnDebug.createTimeResponse()
            val replyCode = createdConn?.replyRemoteCommand(remoteRequestId, response.commandId, response.data)
            if (replyCode == 0) {
              ClientDemoLogger.info("client_command_reply_success request_id=$remoteRequestId")
            } else {
              ClientDemoLogger.warn("client_command_reply_fail request_id=$remoteRequestId code=$replyCode")
            }
          }
        }

        override fun onStreamMessageReceived(
          streamId: Int,
          message: RtcStreamMessage,
        ) {
          if (!isActiveConnectionCallback()) {
            return
          }
          val text = sanitizeClientStreamText(message.data.toString(StandardCharsets.UTF_8))
          ClientDemoLogger.info("client_stream_message_receive stream_id=$streamId text=$text length=${message.data.size} instance=$pageInstanceId")
          showStreamBubble(text)
        }

        override fun onError(
          code: Int,
          message: String,
        ) {
          if (!isActiveConnectionCallback()) {
            return
          }
          ClientDemoLogger.warn("conn_error code=$code msg=$message instance=$pageInstanceId")
          updateSessionState(ClientSessionState.DISCONNECTED)
          handleConnectionBroken(buildConnErrorMessage(code, message))
        }
      }
    createdConn =
      try {
        TiRtcConn(connObserver)
      } catch (throwable: Throwable) {
        clearCurrentOutputs(createdVideoOutput, createdAudioOutput)
        showError(getString(R.string.status_error, "conn create failed: ${throwable.message ?: "unknown"}"))
        return null
      }
    callbackReady = true
    return createdConn
  }

  private fun stopPullFlow(
    source: String,
    showManualStoppedDetail: Boolean,
  ) {
    statusDetailOverride = if (showManualStoppedDetail) getString(R.string.status_overlay_manual_stop_detail) else null
    teardownConnectionOnly(source)
    updateSessionState(ClientSessionState.DISCONNECTED)
    updateViewState(ClientViewState.IDLE)
  }

  private fun initializeRuntimeIfNeeded(config: ClientSessionConfig): Boolean {
    val code = ClientRuntimeBootstrap.ensureInitialized(applicationContext, config.serviceEntry)
    if (code != 0) {
      showError(getString(R.string.status_runtime_failed, code, "initialize failed"))
      return false
    }
    return true
  }

  private fun clearCurrentOutputs(
    video: TiRtcVideoOutput,
    audio: TiRtcAudioOutput,
  ) {
    if (videoOutput === video) {
      videoOutput = null
    }
    if (audioOutput === audio) {
      audioOutput = null
    }
    runOnMainThread {
      video.detach()
      video.destroy()
    }
    audio.destroy()
  }

  private fun runOnIo(
    taskName: String,
    action: () -> Unit,
  ) {
    try {
      sdkIoExecutor.execute {
        try {
          action()
        } catch (throwable: Throwable) {
          ClientDemoLogger.warn("sdk_io_task_failed task=$taskName msg=${throwable.message ?: "unknown"} instance=$pageInstanceId")
        }
      }
    } catch (throwable: Throwable) {
      ClientDemoLogger.warn("sdk_io_task_schedule_failed task=$taskName msg=${throwable.message ?: "unknown"} instance=$pageInstanceId")
    }
  }

  private fun showError(message: String) {
    statusDetailOverride = message
    updateViewState(ClientViewState.IDLE)
    runOnMainThread {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    ClientDemoLogger.warn("state_error message=$message")
  }

  private fun updateViewState(next: ClientViewState) {
    runOnMainThread {
      viewState = next
      refreshStatusUi(next)
    }
    ClientDemoLogger.info("state=$next")
  }

  private fun setPlaybackLoadingState(next: ClientPlaybackLoadingState) {
    runOnMainThread {
      if (playbackLoadingState == next) {
        return@runOnMainThread
      }
      playbackLoadingState = next
      refreshStatusUi(viewState)
    }
    ClientDemoLogger.info("playback_loading_state=$next")
  }

  private fun refreshStatusUi(state: ClientViewState) {
    renderClientViewState(
      state = state,
      playbackLoadingState = playbackLoadingState,
      hasConnectedAtLeastOnce = hasConnectedAtLeastOnce,
      statusDetailOverride = statusDetailOverride,
      views =
        ClientStatusViews(
          loadingView = binding.loading,
          videoStatusStack = binding.videoStatusStack,
          videoDisconnectMask = binding.videoDisconnectMask,
          videoOverlayTitle = binding.videoOverlayTitle,
          emptyHintText = binding.emptyHintText,
          togglePlayButton = binding.togglePlayButton,
        ),
    )
  }

  private fun updateSessionState(next: ClientSessionState) {
    runOnMainThread {
      if (sessionState == next) {
        return@runOnMainThread
      }
      sessionState = next
      binding.clientCommandGetTimeButton.isEnabled = sessionState == ClientSessionState.CONNECTED
    }
  }

  private fun runOnMainThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action()
    } else {
      mainHandler.post(action)
    }
  }
}
