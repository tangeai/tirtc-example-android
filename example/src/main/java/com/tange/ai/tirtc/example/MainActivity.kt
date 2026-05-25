package com.tange.ai.tirtc.example

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
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
import com.tange.ai.tirtc.TiRtcInputStateListener
import com.tange.ai.tirtc.TiRtcLogUploadCallback
import com.tange.ai.tirtc.TiRtcLogging
import com.tange.ai.tirtc.TiRtcOutputBufferStrategy
import com.tange.ai.tirtc.TiRtcVideoCodec
import com.tange.ai.tirtc.TiRtcVideoEncoderPreference
import com.tange.ai.tirtc.TiRtcVideoFrameRate
import com.tange.ai.tirtc.TiRtcVideoInput
import com.tange.ai.tirtc.TiRtcVideoInputActualConfigListener
import com.tange.ai.tirtc.TiRtcVideoInputOptions
import com.tange.ai.tirtc.TiRtcVideoOutput
import com.tange.ai.tirtc.TiRtcVideoOutputOptions
import com.tange.ai.tirtc.TiRtcVideoOutputRenderSizeListener
import com.tange.ai.tirtc.TiRtcVideoOutputStateListener
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import org.json.JSONObject

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
        val appIdField = editText("app_id", clientConfig.appId, viewId = R.id.field_app_id)
        val endpointField = editText("endpoint（可选）", clientConfig.endpoint, viewId = R.id.field_endpoint)
        val remoteIdField = editText("remote_id", clientConfig.remoteId, viewId = R.id.field_remote_id)
        val audioStreamField = editText("audio stream id", clientConfig.audioStreamId.toString(), viewId = R.id.field_audio_stream_id)
        val videoStreamField = editText("video stream id", clientConfig.videoStreamId.toString(), viewId = R.id.field_video_stream_id)
        val tokenField = editText("token", clientConfig.token, multiLine = true, viewId = R.id.field_token)
        setContentView(
            page {
                header(
                    title = "Ti RTC",
                    primaryAction = "偏好设置" to { showSettings() },
                    secondaryAction = "扫一扫" to { showClientQr(appIdField, endpointField, remoteIdField, tokenField) },
                )
                addView(fieldBlock(appIdField))
                addView(fieldBlock(endpointField))
                addView(fieldBlock(remoteIdField))
                addView(twoColumnFields(audioStreamField, videoStreamField))
                addView(fieldBlock(tokenField))
                addView(
                    primaryButton("开始播放") {
                        val next = readClientConfig(
                            appIdField = appIdField,
                            endpointField = endpointField,
                            remoteIdField = remoteIdField,
                            audioStreamField = audioStreamField,
                            videoStreamField = videoStreamField,
                            tokenField = tokenField,
                        ) ?: return@primaryButton
                        clientConfig = next
                        showPlayer(next)
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
        val console = CheckBox(this).apply {
            text = "Console log"
            isChecked = settings.consoleLogEnabled
            setTextColor(ExampleTheme.textPrimary)
        }
        val decoder = spinner(DecoderPreference.values().map { it.label }, settings.decoderPreference.ordinal)
        val buffer =
            spinner(
                listOf("Automatic", "NoBuffer"),
                if (settings.outputBufferStrategy == TiRtcOutputBufferStrategy.NO_BUFFER) 1 else 0,
            )
        val facing =
            spinner(
                listOf("Front", "Back"),
                if (settings.cameraFacing == TiRtcCameraFacing.BACK) 1 else 0,
            )
        val codec =
            spinner(
                listOf("H264", "H265", "MJPEG"),
                when (settings.videoCodec) {
                    TiRtcVideoCodec.H265 -> 1
                    TiRtcVideoCodec.MJPEG -> 2
                    TiRtcVideoCodec.H264 -> 0
                },
            )
        val encoder =
            spinner(
                listOf("Auto", "Software", "Hardware"),
                when (settings.encoderPreference) {
                    TiRtcVideoEncoderPreference.SOFTWARE -> 1
                    TiRtcVideoEncoderPreference.HARDWARE -> 2
                    TiRtcVideoEncoderPreference.AUTO -> 0
                },
            )
        setContentView(
            page {
                navigationHeader("偏好设置") { showConfigure() }
                addView(sectionTitle("Client"))
                addView(spinnerBlock("Decoder preference", decoder))
                addView(spinnerBlock("Output buffer policy", buffer))
                addView(sectionTitle("Device"))
                addView(spinnerBlock("Camera facing", facing))
                addView(spinnerBlock("Video codec", codec))
                addView(spinnerBlock("Encoder preference", encoder))
                addView(sectionTitle("Logging"))
                addView(surface { addView(console) })
                addView(
                    primaryButton("保存") {
                        settings =
                            ExampleSettings(
                                decoderPreference = DecoderPreference.values()[decoder.selectedItemPosition],
                                outputBufferStrategy =
                                    if (buffer.selectedItemPosition == 1) {
                                        TiRtcOutputBufferStrategy.NO_BUFFER
                                    } else {
                                        TiRtcOutputBufferStrategy.AUTOMATIC
                                    },
                                cameraFacing =
                                    if (facing.selectedItemPosition == 1) {
                                        TiRtcCameraFacing.BACK
                                    } else {
                                        TiRtcCameraFacing.FRONT
                                    },
                                videoCodec =
                                    when (codec.selectedItemPosition) {
                                        1 -> TiRtcVideoCodec.H265
                                        2 -> TiRtcVideoCodec.MJPEG
                                        else -> TiRtcVideoCodec.H264
                                    },
                                encoderPreference =
                                    when (encoder.selectedItemPosition) {
                                        1 -> TiRtcVideoEncoderPreference.SOFTWARE
                                        2 -> TiRtcVideoEncoderPreference.HARDWARE
                                        else -> TiRtcVideoEncoderPreference.AUTO
                                    },
                                consoleLogEnabled = console.isChecked,
                            )
                        showConfigure()
                    },
                )
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
                label = "JSON payload",
                value = CLIENT_QR_SAMPLE,
                multiLine = true,
            )
        val scannerView =
            qrScannerView { raw ->
                val payload = parseClientQr(raw) ?: return@qrScannerView false
                appIdField.setText(payload.appId)
                remoteIdField.setText(payload.remoteId)
                tokenField.setText(payload.token)
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
                addView(fieldBlock(payloadField))
                addView(
                    primaryButton("解析并填充") {
                        val payload = parseClientQr(payloadField.text.toString()) ?: return@primaryButton
                        appIdField.setText(payload.appId)
                        remoteIdField.setText(payload.remoteId)
                        tokenField.setText(payload.token)
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
        val metrics = DownlinkMetricsPanel(
            context = this,
            requestedDecoderPreference = settings.decoderPreference.nativeValue,
            onShowExplanation = { showMetricsExplanation() },
        )
        val bubble = streamBubbleView("等待 stream message")
        statusView = status
        metricsView = null
        downlinkMetricsPanel = metrics
        streamBubble = bubble
        setContentView(
            frameScreen(
                top = playerTopBar(
                    remoteId = config.remoteId,
                    onCommand = { showCommandPanel() },
                    onUploadLogs = { uploadLogs() },
                ),
                stage = videoStage,
                overlay = metrics,
                bottom = bottomControls(
                    bubble = bubble,
                    actionText = "停止播放",
                    action = {
                        stopPlayer()
                        showConfigure()
                    },
                ),
            ),
        )
        startPlayer(config, videoStage)
    }

    private fun showDeviceConfigure() {
        clearActiveScanner()
        stopDevice()
        val endpointField = editText("endpoint（可选）", deviceConfig.endpoint, viewId = R.id.field_endpoint)
        val deviceIdField = editText("device_id", deviceConfig.deviceId, viewId = R.id.field_device_id)
        val secretField =
            editText("device_secret_key", deviceConfig.deviceSecretKey, isSecret = true, viewId = R.id.field_device_secret_key)
        setContentView(
            page {
                navigationHeader("设备端配置") { showConfigure() }
                addView(fieldBlock(endpointField))
                addView(fieldBlock(deviceIdField))
                addView(fieldBlock(secretField))
                addView(
                    outlinedButton("扫一扫") {
                        showDeviceQr(endpointField, deviceIdField, secretField)
                    },
                )
                addView(
                    primaryButton("启动设备端") {
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
        val payloadField = editText("JSON payload", DEVICE_QR_SAMPLE, multiLine = true)
        val scannerView =
            qrScannerView { raw ->
                val payload = parseDeviceQr(raw) ?: return@qrScannerView false
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
                addView(fieldBlock(payloadField))
                addView(
                    primaryButton("解析并填充") {
                        val payload = parseDeviceQr(payloadField.text.toString()) ?: return@primaryButton
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
                top = deviceTopBar(
                    secret = maskSecret(config.deviceSecretKey),
                    onCommand = { showCommandPanel() },
                    onUploadLogs = { uploadLogs() },
                ),
                stage = preview,
                overlay = deviceStatusSurface(metrics),
                bottom = bottomControls(
                    bubble = bubble,
                    actionText = "停止设备端",
                    action = {
                        stopDevice()
                        showDeviceConfigure()
                    },
                ),
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
            return
        }
        val nextConn = TiRtcConn()
        val nextAudio = TiRtcAudioOutput()
        val nextVideo = TiRtcVideoOutput()
        conn = nextConn
        audioOutput = nextAudio
        videoOutput = nextVideo
        nextAudio.onStateChanged = TiRtcAudioOutputStateListener { state -> appendStatus("audio=${state.name}") }
        nextVideo.onStateChanged = TiRtcVideoOutputStateListener { state -> appendStatus("video=${state.name}") }
        nextVideo.onRenderSizeChanged =
            TiRtcVideoOutputRenderSizeListener { size -> appendStatus("video size=${size.width}x${size.height}") }
        nextConn.onCommand = TiRtcConnCommandListener { command, data ->
            appendCommand("received", command, data)
            nextConn.sendCommand(command, data)
        }
        nextConn.onStreamMessage = TiRtcConnStreamMessageListener { streamId, _, data ->
            updateStreamBubble("stream $streamId: ${String(data, StandardCharsets.UTF_8)}")
        }
        nextConn.onStateChanged = TiRtcConnStateListener { state, code ->
            appendStatus("conn=${state.name} code=$code")
            if (state == TiRtcConnState.CONNECTED) {
                val audioCode = nextAudio.attach(nextConn, config.audioStreamId)
                val videoCode = nextVideo.attach(nextConn, config.videoStreamId)
                nextConn.subscribeAudio(config.audioStreamId)
                nextConn.subscribeVideo(config.videoStreamId)
                appendStatus("attach audio=$audioCode video=$videoCode")
            }
        }
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
        service.onConnected = TiRtcConnServiceConnectedListener { connected ->
            acceptedConn = connected
            appendStatus("accepted connection")
            connected.onCommand = TiRtcConnCommandListener { command, data ->
                appendCommand("received", command, data)
                connected.sendCommand(command, data)
            }
            connected.onStreamMessage = TiRtcConnStreamMessageListener { streamId, _, data ->
                updateStreamBubble("stream $streamId: ${String(data, StandardCharsets.UTF_8)}")
            }
            appendStatus("attach mic=${nextAudioInput.attach(connected, DEFAULT_AUDIO_STREAM_ID)}")
            appendStatus("attach camera=${nextVideoInput.attach(connected, DEFAULT_VIDEO_STREAM_ID)}")
            startStreamMessages(connected)
        }
        appendStatus("service start=${service.start()}")
    }

    private fun stopPlayer() {
        metricsTimer?.cancel()
        metricsTimer = null
        downlinkMetricsPanel = null
        videoOutput?.dispose()
        videoOutput = null
        audioOutput?.dispose()
        audioOutput = null
        conn?.dispose()
        conn = null
        TiRtc.shutdown()
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
                            mainHandler.post { updateStreamBubble(String(payload, StandardCharsets.UTF_8)) }
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
        val commandField = editText("command id", "0x54524343")
        val mode = spinner(listOf("text", "hex"), 0)
        val payloadField = editText("payload", "echo", multiLine = true)
        val history = body(commandHistory)
        commandHistoryView = history
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), 0)
                addView(fieldBlock(commandField))
                addView(spinnerBlock("payload mode", mode))
                addView(fieldBlock(payloadField))
                addView(sectionTitle("history"))
                addView(history)
            }
        AlertDialog.Builder(this)
            .setTitle("发送命令")
            .setView(root)
            .setNegativeButton("关闭", null)
            .setPositiveButton("发送") { _, _ ->
                val command = parseCommandId(commandField.text.toString()) ?: return@setPositiveButton
                val payload =
                    if (mode.selectedItemPosition == 1) {
                        parseHex(payloadField.text.toString()) ?: return@setPositiveButton
                    } else {
                        payloadField.text.toString().toByteArray(StandardCharsets.UTF_8)
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
        tokenField: EditText,
    ): ClientConfiguration? {
        val appId = appIdField.text.toString().trim()
        val remoteId = remoteIdField.text.toString().trim()
        val token = tokenField.text.toString().trim()
        if (appId.isBlank() || remoteId.isBlank() || token.isBlank()) {
            toast("请先填写 app_id、remote_id 和 token")
            return null
        }
        return ClientConfiguration(
            appId = appId,
            endpoint = endpointField.text.toString().trim(),
            remoteId = remoteId,
            audioStreamId = audioStreamField.text.toString().toIntOrNull() ?: DEFAULT_AUDIO_STREAM_ID,
            videoStreamId = videoStreamField.text.toString().toIntOrNull() ?: DEFAULT_VIDEO_STREAM_ID,
            token = token,
        )
    }

    private fun parseClientQr(payload: String): ClientConfiguration? {
        return try {
            val json = JSONObject(payload)
            val appId = json.optString("app_id").trim()
            val remoteId = json.optString("remote_id").trim()
            val token = json.optString("token").trim()
            if (appId.isBlank() || remoteId.isBlank() || token.isBlank()) {
                toast("二维码缺少 app_id、remote_id 或 token")
                null
            } else {
                clientConfig.copy(
                    appId = appId,
                    endpoint = json.optString("endpoint").trim(),
                    remoteId = remoteId,
                    token = token,
                )
            }
        } catch (error: Exception) {
            toast("二维码 JSON 无效：${error.message}")
            null
        }
    }

    private fun parseDeviceQr(payload: String): DeviceConfiguration? {
        return try {
            val json = JSONObject(payload)
            val allowed = setOf("endpoint", "device_id", "device_secret_key")
            val keys = json.keys().asSequence().toSet()
            if (!allowed.containsAll(keys)) {
                toast("设备端二维码包含未允许字段")
                return null
            }
            val deviceId = json.optString("device_id").trim()
            val secret = json.optString("device_secret_key")
            if (deviceId.isBlank() || secret.isBlank()) {
                toast("二维码缺少 device_id 或 device_secret_key")
                null
            } else {
                DeviceConfiguration(
                    endpoint = json.optString("endpoint").trim(),
                    deviceId = deviceId,
                    deviceSecretKey = secret,
                )
            }
        } catch (error: Exception) {
            toast("二维码 JSON 无效：${error.message}")
            null
        }
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
        val line = "$direction ${formatCommand(command)} ${String(payload, StandardCharsets.UTF_8)}"
        commandHistory = if (commandHistory == "暂无命令记录") line else "$line\n$commandHistory"
        commandHistoryView?.text = commandHistory
        appendStatus(line)
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

    private fun parseCommandId(text: String): Long? {
        val trimmed = text.trim()
        val value =
            if (trimmed.startsWith("0x", ignoreCase = true)) {
                trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
            } else {
                trimmed.toLongOrNull()
            }
        if (value == null || value !in 0..MAX_COMMAND_ID) {
            toast("命令 ID 必须是 32 位无符号整数")
            return null
        }
        return value
    }

    private fun parseHex(text: String): ByteArray? {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length % 2 != 0 || !compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            toast("HEX 内容必须是偶数位有效字符")
            return null
        }
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun formatCommand(command: Long): String = "0x${command.toString(16).uppercase()}"

    private fun maskSecret(value: String): String {
        if (value.length <= 4) {
            return "****"
        }
        return "${value.take(2)}****${value.takeLast(2)}"
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
        private const val MAX_COMMAND_ID = 0xFFFF_FFFFL
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
