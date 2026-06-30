package com.tange.ai.tirtc.example

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.tange.ai.tirtc.TiRtc
import com.tange.ai.tirtc.TiRtcAudioChannelCount
import com.tange.ai.tirtc.TiRtcAudioCodec
import com.tange.ai.tirtc.TiRtcAudioInput
import com.tange.ai.tirtc.TiRtcAudioInputOptions
import com.tange.ai.tirtc.TiRtcAudioOutput
import com.tange.ai.tirtc.TiRtcAudioOutputOptions
import com.tange.ai.tirtc.TiRtcAudioOutputStateListener
import com.tange.ai.tirtc.TiRtcAudioSampleRate
import com.tange.ai.tirtc.TiRtcCameraFacing
import com.tange.ai.tirtc.TiRtcConn
import com.tange.ai.tirtc.TiRtcConnCommandListener
import com.tange.ai.tirtc.TiRtcConnService
import com.tange.ai.tirtc.TiRtcConnServiceConfig
import com.tange.ai.tirtc.TiRtcConnServiceConnectedListener
import com.tange.ai.tirtc.TiRtcConnServiceErrorListener
import com.tange.ai.tirtc.TiRtcConnServiceStartedListener
import com.tange.ai.tirtc.TiRtcConnServiceStoppedListener
import com.tange.ai.tirtc.TiRtcConnState
import com.tange.ai.tirtc.TiRtcConnStateListener
import com.tange.ai.tirtc.TiRtcConnStreamMessageListener
import com.tange.ai.tirtc.TiRtcInitOptions
import com.tange.ai.tirtc.TiRtcInputErrorListener
import com.tange.ai.tirtc.TiRtcInputStateListener
import com.tange.ai.tirtc.TiRtcLogUploadCallback
import com.tange.ai.tirtc.TiRtcLogging
import com.tange.ai.tirtc.TiRtcVideoFrameRate
import com.tange.ai.tirtc.TiRtcVideoInput
import com.tange.ai.tirtc.TiRtcVideoInputActualConfigListener
import com.tange.ai.tirtc.TiRtcVideoInputOptions
import com.tange.ai.tirtc.TiRtcVideoOutput
import com.tange.ai.tirtc.TiRtcVideoOutputOptions
import com.tange.ai.tirtc.TiRtcVideoOutputRenderSizeListener
import com.tange.ai.tirtc.TiRtcVideoOutputStateListener
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var settings = ExampleSettings()
    private var clientConfig =
        ClientConfiguration(
            appId = "",
            endpoint = "",
            remoteId = "",
            audioStreamId = DEFAULT_AUDIO_STREAM_ID,
            videoStreamId = DEFAULT_VIDEO_STREAM_ID,
            token = "",
        )
    private var deviceConfig = DeviceConfiguration(endpoint = "", deviceId = "", deviceSecretKey = "")
    private var conn: TiRtcConn? = null
    private var audioOutput: TiRtcAudioOutput? = null
    private var videoOutput: TiRtcVideoOutput? = null
    private var connService: TiRtcConnService? = null
    private var acceptedConn: TiRtcConn? = null
    private var playerAudioInput: TiRtcAudioInput? = null
    private var playerTalkbackRunning = false
    private var playerRunning = false
    private var playerConfig: ClientConfiguration? = null
    private var playerStage: FrameLayout? = null
    private var playerLocalAudioButton: TextView? = null
    private var playerDownlinkButton: TextView? = null
    private var audioInput: TiRtcAudioInput? = null
    private var videoInput: TiRtcVideoInput? = null
    private var metricsTimer: Timer? = null
    private var streamTimer: Timer? = null
    private var statusView: TextView? = null
    private var metricsView: TextView? = null
    private var downlinkMetricsPanel: DownlinkMetricsPanel? = null
    private var streamBubble: TextView? = null
    private var commandHistoryView: TextView? = null
    private var commandHistory = "暂无命令记录"
    private var activeScanner: DecoratedBarcodeView? = null
    private var scannerProcessing = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        showConfigure()
    }

    override fun onResume() {
        super.onResume()
        activeScanner?.resume()
    }

    override fun onPause() {
        activeScanner?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        clearActiveScanner()
        stopDevice()
        stopPlayer()
        super.onDestroy()
    }

    private fun showConfigure() {
        clearActiveScanner()
        stopPlayer()
        statusView = null
        val appIdField = editText("TiRTC 应用标识，进入播放页前必须提供。", clientConfig.appId, viewId = R.id.field_app_id)
        val endpointField = editText("接入的云端环境，留空则使用默认环境。", clientConfig.endpoint, viewId = R.id.field_endpoint)
        val remoteIdField = editText("待连接的远端目标 ID", clientConfig.remoteId, viewId = R.id.field_remote_id)
        val audioStreamField = editText("音频流 ID，默认 10", clientConfig.audioStreamId.toString(), viewId = R.id.field_audio_stream_id)
        val videoStreamField = editText("视频流 ID，默认 11", clientConfig.videoStreamId.toString(), viewId = R.id.field_video_stream_id)
        val tokenSource =
            spinner(
                listOf("tokenIssuer", "oneTimeToken"),
                if (clientConfig.tokenSource == DemoTokenSource.ISSUER) 0 else 1,
            )
        val tokenIssuerField = editText("Token issuer base URL", clientConfig.tokenIssuerBaseUrl)
        val tokenField =
            editText(
                "进行一次连接所需的一次性 token",
                clientConfig.oneTimeToken.ifBlank { clientConfig.token },
                multiLine = true,
                viewId = R.id.field_token,
            )
        setContentView(
            page {
                header(
                    title = "Ti RTC",
                    primaryAction = "偏好设置" to { showSettings() },
                )
                addViewWithMargin(fieldBlock("app_id", appIdField), bottom = 16)
                addViewWithMargin(fieldBlock("endpoint", endpointField), bottom = 16)
                addViewWithMargin(fieldBlock("remote_id", remoteIdField), bottom = 16)
                addViewWithMargin(
                    twoColumnFields(
                        "audio_stream_id",
                        audioStreamField,
                        "video_stream_id",
                        videoStreamField,
                    ),
                    bottom = 16,
                )
                addViewWithMargin(
                    surface {
                        addView(sectionTitle("连接 Token"))
                        addView(inputLabel("token acquisition"))
                        addViewWithMargin(tokenSource, bottom = 16)
                        addViewWithMargin(fieldBlock("Token 签发服务地址", tokenIssuerField), bottom = 16)
                        addViewWithMargin(fieldBlock("一次性连接 Token", tokenField), bottom = 12)
                        addView(
                            outlinedButton("扫一扫") {
                                showClientQr(appIdField, endpointField, remoteIdField, tokenField)
                            },
                        )
                    },
                    bottom = 20,
                )
                addView(
                    primaryButton("进入播放页面") {
                        val next =
                            readClientConfig(
                                appIdField = appIdField,
                                endpointField = endpointField,
                                remoteIdField = remoteIdField,
                                audioStreamField = audioStreamField,
                                videoStreamField = videoStreamField,
                                tokenSource = tokenSource.selectedItemPosition,
                                tokenIssuerField = tokenIssuerField,
                                tokenField = tokenField,
                            ) ?: return@primaryButton
                        clientConfig = next
                        resolveTokenAndShowPlayer(next)
                    },
                )
                addView(
                    linkButton("或者，将本机作为设备端启动") {
                        showDeviceConfigure()
                    },
                )
            },
        )
    }

    private fun showSettings() {
        clearActiveScanner()
        showExampleSettingsPage(
            settings = settings,
            onBack = { showConfigure() },
            onSave = { next ->
                settings = next
                showConfigure()
            },
        )
    }

    private fun showClientQr(
        appIdField: EditText,
        endpointField: EditText,
        remoteIdField: EditText,
        tokenField: EditText,
    ) {
        val payloadField =
            editText(
                placeholder = CLIENT_QR_SAMPLE,
                value = CLIENT_QR_SAMPLE,
                multiLine = true,
            )
        val scannerView =
            qrScannerView { raw ->
                val payload = parseClientQrPayload(raw, clientConfig, ::toast) ?: return@qrScannerView false
                appIdField.setText(payload.appId)
                remoteIdField.setText(payload.remoteId)
                tokenField.setText(payload.oneTimeToken)
                if (payload.endpoint.isNotBlank()) {
                    endpointField.setText(payload.endpoint)
                }
                clientConfig = payload
                showConfigure()
                true
            }
        setContentView(
            page {
                navigationHeader("扫一扫") { showConfigure() }
                addView(scannerPanel(scannerView))
                addView(qrGuide("将二维码完整放入方框内，系统会自动识别并填充 app_id、remote_id、token。"))
                addViewWithMargin(
                    fieldBlock("JSON payload", payloadField),
                    bottom = 20,
                )
                addView(
                    primaryButton("解析并填充") {
                        val payload = parseClientQrPayload(payloadField.text.toString(), clientConfig, ::toast) ?: return@primaryButton
                        appIdField.setText(payload.appId)
                        remoteIdField.setText(payload.remoteId)
                        tokenField.setText(payload.oneTimeToken)
                        if (payload.endpoint.isNotBlank()) {
                            endpointField.setText(payload.endpoint)
                        }
                        clientConfig = payload
                        showConfigure()
                    },
                )
            },
        )
        activateScanner(scannerView)
    }

    private fun showPlayer(config: ClientConfiguration) {
        clearActiveScanner()
        val videoStage = videoPanel("远端视频")
        val status = body("正在初始化")
        val metrics =
            DownlinkMetricsPanel(
                context = this,
                requestedDecoderPreference = settings.decoderPreference.nativeValue,
                onShowExplanation = { showMetricsExplanation() },
            )
        val bubble = streamBubbleView("等待 stream message")
        val localAudioButton =
            compactFilledButton(
                text = "启动麦克风",
                backgroundColor = ExampleTheme.surface,
                foregroundColor = ExampleTheme.primary,
            ) {
                togglePlayerTalkback()
            }
        val downlinkButton =
            compactFilledButton("连接中") {
                togglePlayerDownlink()
            }
        playerConfig = config
        playerStage = videoStage
        playerLocalAudioButton = localAudioButton
        playerDownlinkButton = downlinkButton
        statusView = status
        metricsView = null
        downlinkMetricsPanel = metrics
        streamBubble = bubble
        setPlayerControlState(connecting = true, running = false, localAudioEnabled = false)
        setContentView(
            frameScreen(
                top =
                    playerTopBar(
                        remoteId = config.remoteId,
                        onBack = {
                            stopPlayer()
                            showConfigure()
                        },
                        onCommand = { showCommandPanel() },
                        onUploadLogs = { uploadLogs() },
                    ),
                stage = videoStage,
                overlay = metrics,
                bottom =
                    playerBottomControls(
                        bubble = bubble,
                        localAudioButton = localAudioButton,
                        downlinkButton = downlinkButton,
                    ),
            ),
        )
        startPlayer(config, videoStage)
    }

    private fun showDeviceConfigure() {
        clearActiveScanner()
        stopDevice()
        val endpointField = editText("接入的云端环境，留空则使用默认环境。", deviceConfig.endpoint, viewId = R.id.field_endpoint)
        val deviceIdField = editText("设备端身份标识。", deviceConfig.deviceId, viewId = R.id.field_device_id)
        val secretField =
            editText("设备端连接密钥。", deviceConfig.deviceSecretKey, isSecret = true, viewId = R.id.field_device_secret_key)
        setContentView(
            page {
                navigationHeader("设备端配置") { showConfigure() }
                addViewWithMargin(fieldBlock("endpoint", endpointField), bottom = 16)
                addViewWithMargin(fieldBlock("device_id", deviceIdField), bottom = 16)
                addViewWithMargin(fieldBlock("device_secret_key", secretField), bottom = 20)
                addView(
                    outlinedButton("扫一扫") {
                        showDeviceQr(endpointField, deviceIdField, secretField)
                    },
                )
                addView(
                    primaryButton("进入设备端") {
                        val next =
                            DeviceConfiguration(
                                endpoint = endpointField.text.toString().trim(),
                                deviceId = deviceIdField.text.toString().trim(),
                                deviceSecretKey = secretField.text.toString(),
                            )
                        if (next.deviceId.isBlank() || next.deviceSecretKey.isBlank()) {
                            toast("请填写 device_id 和 device_secret_key")
                            return@primaryButton
                        }
                        deviceConfig = next
                        showDevice(next)
                    },
                )
            },
        )
    }

    private fun showDeviceQr(
        endpointField: EditText,
        deviceIdField: EditText,
        secretField: EditText,
    ) {
        val payloadField = editText(DEVICE_QR_SAMPLE, DEVICE_QR_SAMPLE, multiLine = true)
        val scannerView =
            qrScannerView { raw ->
                val payload = parseDeviceQrPayload(raw, ::toast) ?: return@qrScannerView false
                endpointField.setText(payload.endpoint)
                deviceIdField.setText(payload.deviceId)
                secretField.setText(payload.deviceSecretKey)
                deviceConfig = payload
                showDeviceConfigure()
                true
            }
        setContentView(
            page {
                navigationHeader("设备端扫一扫") { showDeviceConfigure() }
                addView(scannerPanel(scannerView))
                addView(qrGuide("使用 JSON，并且只包含 device_id、device_secret_key，以及可选的 endpoint。"))
                addViewWithMargin(
                    fieldBlock("JSON payload", payloadField),
                    bottom = 20,
                )
                addView(
                    primaryButton("解析并填充") {
                        val payload = parseDeviceQrPayload(payloadField.text.toString(), ::toast) ?: return@primaryButton
                        endpointField.setText(payload.endpoint)
                        deviceIdField.setText(payload.deviceId)
                        secretField.setText(payload.deviceSecretKey)
                        deviceConfig = payload
                        showDeviceConfigure()
                    },
                )
            },
        )
        activateScanner(scannerView)
    }

    private fun showDevice(config: DeviceConfiguration) {
        clearActiveScanner()
        val preview = videoPanel("本地预览")
        val status = body("设备端启动中")
        val metrics = body("Service waiting")
        val bubble = streamBubbleView("等待连接")
        statusView = status
        metricsView = metrics
        downlinkMetricsPanel = null
        streamBubble = bubble
        setContentView(
            frameScreen(
                top =
                    deviceTopBar(
                        deviceId = config.deviceId,
                        onBack = {
                            stopDevice()
                            showDeviceConfigure()
                        },
                        onCommand = { showCommandPanel() },
                        onUploadLogs = { uploadLogs() },
                    ),
                stage = preview,
                overlay = View(this),
            ),
        )
        startDevice(config, preview)
    }

    private fun startPlayer(
        config: ClientConfiguration,
        stage: FrameLayout,
    ) {
        val initCode =
            TiRtc.initialize(
                this,
                TiRtcInitOptions(
                    appId = config.appId,
                    endpoint = config.endpoint,
                    consoleLogEnabled = settings.consoleLogEnabled,
                ),
            )
        appendStatus("initialize code=$initCode")
        if (initCode != 0) {
            setPlayerControlState(connecting = false, running = false, localAudioEnabled = false)
            return
        }
        val nextConn = TiRtcConn()
        val nextAudio = TiRtcAudioOutput()
        val nextVideo = TiRtcVideoOutput()
        val nextTalkback = TiRtcAudioInput()
        conn = nextConn
        audioOutput = nextAudio
        videoOutput = nextVideo
        playerAudioInput = nextTalkback
        playerTalkbackRunning = false
        nextAudio.onStateChanged = TiRtcAudioOutputStateListener { state -> appendStatus("audio=${state.name}") }
        nextVideo.onStateChanged = TiRtcVideoOutputStateListener { state -> appendStatus("video=${state.name}") }
        nextTalkback.onStateChanged = TiRtcInputStateListener { state -> appendStatus("talkback=${state.name}") }
        nextTalkback.onError = TiRtcInputErrorListener { code, message ->
            appendStatus("talkback error=$code ${message ?: ""}")
        }
        nextVideo.onRenderSizeChanged =
            TiRtcVideoOutputRenderSizeListener { size -> appendStatus("video size=${size.width}x${size.height}") }
        nextConn.onCommand =
            TiRtcConnCommandListener { command, data ->
                handleIncomingCommand(nextConn, command, data)
            }
        nextConn.onStreamMessage =
            TiRtcConnStreamMessageListener { streamId, _, data ->
                updateStreamBubble("stream $streamId: ${payloadText(data)}")
            }
        nextConn.onStateChanged =
            TiRtcConnStateListener { state, code ->
                appendStatus("conn=${state.name} code=$code")
                if (state == TiRtcConnState.CONNECTED) {
                    setPlayerControlState(connecting = false, running = true, localAudioEnabled = true)
                    val audioCode = nextAudio.attach(nextConn, config.audioStreamId)
                    val videoCode = nextVideo.attach(nextConn, config.videoStreamId)
                    nextConn.subscribeAudio(config.audioStreamId)
                    nextConn.subscribeVideo(config.videoStreamId)
                    appendStatus("attach audio=$audioCode video=$videoCode")
                    appendStatus("talkback ready stream=${settings.localAudioStreamId}")
                }
            }
        nextTalkback.setOptions(settings.localAudioOptions())
        nextAudio.configure(TiRtcAudioOutputOptions(bufferStrategy = settings.outputBufferStrategy))
        nextVideo.setOptions(
            TiRtcVideoOutputOptions(
                decoderPreference = settings.decoderPreference.nativeValue,
                bufferStrategy = settings.outputBufferStrategy,
            ),
        )
        appendStatus("view=${nextVideo.attachView(stage)}")
        appendStatus("connect=${nextConn.connect(config.remoteId, config.token)}")
        startMetricsPolling()
    }

    private fun startDevice(
        config: DeviceConfiguration,
        preview: FrameLayout,
    ) {
        val initCode =
            TiRtc.initialize(
                this,
                TiRtcInitOptions(endpoint = config.endpoint, consoleLogEnabled = settings.consoleLogEnabled),
            )
        appendStatus("initialize code=$initCode")
        if (initCode != 0) {
            return
        }
        val service = TiRtcConnService(TiRtcConnServiceConfig(config.deviceId, config.deviceSecretKey))
        val nextAudioInput = TiRtcAudioInput()
        val nextVideoInput = TiRtcVideoInput()
        connService = service
        audioInput = nextAudioInput
        videoInput = nextVideoInput
        nextAudioInput.onStateChanged = TiRtcInputStateListener { appendStatus("mic=${it.name}") }
        nextVideoInput.onStateChanged = TiRtcInputStateListener { appendStatus("camera=${it.name}") }
        nextVideoInput.onActualConfigChanged =
            TiRtcVideoInputActualConfigListener { size, fps -> appendStatus("camera=${size.width}x${size.height}@$fps") }
        nextAudioInput.setOptions(
            TiRtcAudioInputOptions(
                codec = TiRtcAudioCodec.G711A,
                sampleRate = TiRtcAudioSampleRate.RATE_16K,
                channels = TiRtcAudioChannelCount.MONO,
            ),
        )
        nextVideoInput.setOptions(
            TiRtcVideoInputOptions(
                codec = settings.videoCodec,
                width = 640,
                height = 480,
                frameRate = TiRtcVideoFrameRate.FPS_15,
                cameraFacing = settings.cameraFacing,
                encoderPreference = settings.encoderPreference,
            ),
        )
        appendStatus("preview=${nextVideoInput.attachPreview(preview)}")
        appendStatus("mic start=${nextAudioInput.start()} camera start=${nextVideoInput.start()}")
        service.onStarted = TiRtcConnServiceStartedListener { appendStatus("service=started") }
        service.onStopped = TiRtcConnServiceStoppedListener { appendStatus("service=stopped") }
        service.onError = TiRtcConnServiceErrorListener { code, message -> appendStatus("service error=$code ${message ?: ""}") }
        service.onConnected =
            TiRtcConnServiceConnectedListener { connected ->
                acceptedConn = connected
                appendStatus("accepted connection")
                connected.onCommand =
                    TiRtcConnCommandListener { command, data ->
                        handleIncomingCommand(connected, command, data)
                    }
                connected.onStreamMessage =
                    TiRtcConnStreamMessageListener { streamId, _, data ->
                        updateStreamBubble("stream $streamId: ${payloadText(data)}")
                    }
                appendStatus("attach mic=${nextAudioInput.attach(connected, DEFAULT_AUDIO_STREAM_ID)}")
                appendStatus("attach camera=${nextVideoInput.attach(connected, DEFAULT_VIDEO_STREAM_ID)}")
                startStreamMessages(connected)
            }
        appendStatus("service start=${service.start()}")
    }

    private fun stopPlayer(clearPageRefs: Boolean = true) {
        metricsTimer?.cancel()
        metricsTimer = null
        stopPlayerTalkback()
        playerAudioInput?.dispose()
        playerAudioInput = null
        playerTalkbackRunning = false
        videoOutput?.dispose()
        videoOutput = null
        audioOutput?.dispose()
        audioOutput = null
        conn?.dispose()
        conn = null
        playerRunning = false
        setPlayerControlState(connecting = false, running = false, localAudioEnabled = false)
        if (clearPageRefs) {
            downlinkMetricsPanel = null
            playerConfig = null
            playerStage = null
            playerLocalAudioButton = null
            playerDownlinkButton = null
        }
        TiRtc.shutdown()
    }

    private fun togglePlayerDownlink() {
        if (playerRunning) {
            stopPlayer(clearPageRefs = false)
            appendStatus("Downlink stopped.")
            return
        }
        val config = playerConfig ?: return
        val stage = playerStage ?: return
        setPlayerControlState(connecting = true, running = false, localAudioEnabled = false)
        startPlayer(config, stage)
    }

    private fun togglePlayerTalkback() {
        if (playerTalkbackRunning) {
            stopPlayerTalkback()
        } else {
            startPlayerTalkback()
        }
    }

    private fun startPlayerTalkback() {
        val connection = conn
        val input = playerAudioInput
        if (connection == null || input == null || connection.state != TiRtcConnState.CONNECTED) {
            appendStatus("talkback waiting for connected client")
            return
        }
        val optionsCode = input.setOptions(settings.localAudioOptions())
        if (optionsCode != 0) {
            appendStatus("talkback options=$optionsCode")
            return
        }
        val attachCode = input.attach(connection, settings.localAudioStreamId)
        if (attachCode != 0) {
            appendStatus("talkback attach=$attachCode")
            return
        }
        val startCode = input.start()
        playerTalkbackRunning = startCode == 0
        updatePlayerLocalAudioButton(enabled = true)
        appendStatus("talkback start=$startCode stream=${settings.localAudioStreamId}")
    }

    private fun stopPlayerTalkback() {
        val input = playerAudioInput ?: return
        val connection = conn
        val detachCode = if (connection != null) input.detach(connection) else 0
        val stopCode = input.stop()
        playerTalkbackRunning = false
        updatePlayerLocalAudioButton(enabled = connection?.state == TiRtcConnState.CONNECTED)
        appendStatus("talkback stop=$stopCode detach=$detachCode")
    }

    private fun setPlayerControlState(
        connecting: Boolean,
        running: Boolean,
        localAudioEnabled: Boolean,
    ) {
        playerRunning = running
        playerDownlinkButton?.apply {
            text =
                when {
                    connecting -> "连接中"
                    running -> "停止播放"
                    else -> "开始播放"
                }
            isEnabled = !connecting
            alpha = if (isEnabled) 1.0f else 0.55f
        }
        updatePlayerLocalAudioButton(enabled = localAudioEnabled)
    }

    private fun updatePlayerLocalAudioButton(enabled: Boolean) {
        playerLocalAudioButton?.apply {
            text = if (playerTalkbackRunning) "停止麦克风" else "启动麦克风"
            isEnabled = enabled
            alpha = if (enabled) 1.0f else 0.55f
        }
    }

    private fun stopDevice() {
        streamTimer?.cancel()
        streamTimer = null
        videoInput?.dispose()
        videoInput = null
        audioInput?.dispose()
        audioInput = null
        acceptedConn?.dispose()
        acceptedConn = null
        connService?.dispose()
        connService = null
        TiRtc.shutdown()
    }

    private fun startMetricsPolling() {
        metricsTimer?.cancel()
        metricsTimer =
            Timer("tirtc-android-example-metrics", true).also { timer ->
                timer.scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            mainHandler.post { refreshMetrics() }
                        }
                    },
                    0L,
                    METRICS_PERIOD_MS,
                )
            }
    }

    private fun startStreamMessages(connection: TiRtcConn) {
        streamTimer?.cancel()
        streamTimer =
            Timer("tirtc-android-example-stream-message", true).also { timer ->
                timer.scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            val payload = "android-device ${System.currentTimeMillis()}".toByteArray()
                            connection.sendStreamMessage(DEFAULT_VIDEO_STREAM_ID, System.currentTimeMillis(), payload)
                            mainHandler.post { updateStreamBubble(payloadText(payload)) }
                        }
                    },
                    STREAM_MESSAGE_PERIOD_MS,
                    STREAM_MESSAGE_PERIOD_MS,
                )
            }
    }

    private fun refreshMetrics() {
        val connection = conn
        val audio = audioOutput
        val video = videoOutput
        downlinkMetricsPanel?.render(connection, audio, video)
    }

    private fun showMetricsExplanation() {
        AlertDialog.Builder(this)
            .setTitle("指标说明")
            .setMessage(DOWNLINK_METRICS_EXPLANATION)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showCommandPanel() {
        val commandField = editText("0x00000000", formatCommandId(DEMO_CALL_COMMAND_ID))
        val preset = spinner(listOf("echo", CALL_START, CALL_READY, CALL_REJECT), 0)
        val mode = spinner(listOf("text", "hex"), 0)
        val payloadField = editText("输入文本内容", CALL_START, multiLine = true)
        val history = body(commandHistory)
        commandHistoryView = history
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), 0)
                addViewWithMargin(fieldBlock("命令 ID", commandField), bottom = 16)
                addView(spinnerBlock("call command schema", preset))
                addView(spinnerBlock("payload mode", mode))
                addViewWithMargin(fieldBlock("命令内容", payloadField), bottom = 16)
                addView(sectionTitle("history"))
                addView(history)
            }
        AlertDialog.Builder(this)
            .setTitle("发送命令")
            .setView(root)
            .setNegativeButton("关闭", null)
            .setPositiveButton("发送") { _, _ ->
                val command = parseCommandIdOrNull(commandField.text.toString(), ::toast) ?: return@setPositiveButton
                val payload =
                    if (preset.selectedItemPosition > 0) {
                        utf8Payload(demoCommandPresetPayload(preset.selectedItemPosition))
                    } else if (mode.selectedItemPosition == 1) {
                        parseHexPayloadOrNull(payloadField.text.toString(), ::toast) ?: return@setPositiveButton
                    } else {
                        utf8Payload(payloadField.text.toString())
                    }
                val code = (conn ?: acceptedConn)?.sendCommand(command, payload) ?: -1
                appendCommand("sent code=$code", command, payload)
            }
            .show()
    }

    private fun uploadLogs() {
        appendStatus("log upload=start")
        TiRtcLogging.upload(
            TiRtcLogUploadCallback { code, logId ->
                appendStatus("log upload code=$code id=${logId ?: ""}")
            },
        )
    }

    private fun readClientConfig(
        appIdField: EditText,
        endpointField: EditText,
        remoteIdField: EditText,
        audioStreamField: EditText,
        videoStreamField: EditText,
        tokenSource: Int,
        tokenIssuerField: EditText,
        tokenField: EditText,
    ): ClientConfiguration? {
        val appId = appIdField.text.toString().trim()
        val remoteId = remoteIdField.text.toString().trim()
        val source = if (tokenSource == 0) DemoTokenSource.ISSUER else DemoTokenSource.ONE_TIME
        val tokenIssuerBaseUrl = tokenIssuerField.text.toString().trim()
        val oneTimeToken = tokenField.text.toString().trim()
        if (appId.isBlank() || remoteId.isBlank()) {
            toast("请先填写 app_id 和 remote_id")
            return null
        }
        if (source == DemoTokenSource.ISSUER && tokenIssuerBaseUrl.isBlank()) {
            toast("请填写 tokenIssuerBaseUrl")
            return null
        }
        if (source == DemoTokenSource.ONE_TIME && oneTimeToken.isBlank()) {
            toast("请填写 oneTimeToken")
            return null
        }
        return ClientConfiguration(
            appId = appId,
            endpoint = endpointField.text.toString().trim(),
            remoteId = remoteId,
            audioStreamId = audioStreamField.text.toString().toIntOrNull() ?: DEFAULT_AUDIO_STREAM_ID,
            videoStreamId = videoStreamField.text.toString().toIntOrNull() ?: DEFAULT_VIDEO_STREAM_ID,
            token = oneTimeToken,
            tokenSource = source,
            tokenIssuerBaseUrl = tokenIssuerBaseUrl,
            oneTimeToken = oneTimeToken,
        )
    }

    private fun resolveTokenAndShowPlayer(config: ClientConfiguration) {
        if (config.tokenSource == DemoTokenSource.ONE_TIME) {
            try {
                val resolved = resolveDemoToken(config)
                clientConfig = resolved
                showPlayer(resolved)
            } catch (error: Exception) {
                toast("token 无效：${error.message}")
            }
            return
        }
        toast("token acquisition=start")
        Thread {
            val result =
                runCatching {
                    resolveDemoToken(config)
                }
            mainHandler.post {
                result
                    .onSuccess { resolved ->
                        clientConfig = resolved
                        showPlayer(resolved)
                    }
                    .onFailure { error -> toast("token issuer 失败：${error.message}") }
            }
        }.start()
    }

    private fun appendStatus(message: String) {
        mainHandler.post {
            val current = statusView?.text?.toString().orEmpty()
            statusView?.text = if (current.isBlank()) message else "$current\n$message"
        }
    }

    private fun appendCommand(
        direction: String,
        command: Long,
        payload: ByteArray,
    ) {
        val line = "$direction ${formatCommandId(command)} ${payloadText(payload)}"
        commandHistory = if (commandHistory == "暂无命令记录") line else "$line\n$commandHistory"
        commandHistoryView?.text = commandHistory
        appendStatus(line)
    }

    private fun handleIncomingCommand(
        connection: TiRtcConn,
        command: Long,
        payload: ByteArray,
    ) {
        appendCommand("received", command, payload)
        val responsePayload = demoCommandResponsePayload(command, payload) ?: return
        val code = connection.sendCommand(command, responsePayload)
        appendCommand("sent code=$code", command, responsePayload)
    }

    private fun updateStreamBubble(text: String) {
        streamBubble?.text = text
    }

    private fun qrScannerView(onPayload: (String) -> Boolean): DecoratedBarcodeView {
        return DecoratedBarcodeView(this).apply {
            setStatusText("")
            decodeContinuous(
                object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        val raw = result.text?.trim().orEmpty()
                        if (raw.isBlank() || scannerProcessing) {
                            return
                        }
                        scannerProcessing = true
                        mainHandler.post {
                            if (onPayload(raw)) {
                                clearActiveScanner()
                                return@post
                            }
                            mainHandler.postDelayed({ scannerProcessing = false }, SCANNER_RETRY_DELAY_MS)
                        }
                    }

                    override fun possibleResultPoints(resultPoints: List<ResultPoint>) = Unit
                },
            )
        }
    }

    private fun activateScanner(scannerView: DecoratedBarcodeView) {
        clearActiveScanner()
        activeScanner = scannerView
        scannerView.resume()
    }

    private fun clearActiveScanner() {
        activeScanner?.pause()
        activeScanner = null
        scannerProcessing = false
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing =
            permissions.filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val DEFAULT_AUDIO_STREAM_ID = 10
        private const val DEFAULT_VIDEO_STREAM_ID = 11
        private const val METRICS_PERIOD_MS = 1000L
        private const val STREAM_MESSAGE_PERIOD_MS = 3000L
        private const val SCANNER_RETRY_DELAY_MS = 900L
        private const val CLIENT_QR_SAMPLE =
            "{\n" +
                "  \"app_id\": \"demo-app\",\n" +
                "  \"remote_id\": \"TESTTIRTC01\",\n" +
                "  \"token\": \"token\",\n" +
                "  \"endpoint\": \"https://example.com\"\n" +
                "}"
        private const val DEVICE_QR_SAMPLE =
            "{\n" +
                "  \"device_id\": \"TESTTIRTC01\",\n" +
                "  \"device_secret_key\": \"secret\",\n" +
                "  \"endpoint\": \"https://example.com\"\n" +
                "}"
        private const val DOWNLINK_METRICS_EXPLANATION =
            "【连接耗时】：从点击开始连接，到 runtime 确认连接成功的时间。只表示连接建立用了多久，不表示画面已经出来。\n\n" +
                "【首帧耗时】：从点击开始连接，到第一个视频帧真正显示成功的时间。\n\n" +
                "【卡顿统计】：第一个视频帧真正显示成功后，才开始统计本次播放的卡顿；连接中、等首帧、页面看不见、停止播放后的空窗不算卡顿。\n\n" +
                "【码率 / 速率】：码率、接收 FPS、渲染 FPS 和音频包率来自 runtime 最近一个已闭合窗口。\n\n" +
                "【音频卡顿】：统计本机音频输出已经开始后，系统输出回调取不到可播放数据而产生的停滞。\n\n" +
                "【视频 / 音频本机延迟】：表示从本机接收到远端编码包，到 runtime 交给本机输出并返回所花的时间。"
    }
}
