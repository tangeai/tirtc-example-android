package com.tange.ai.tirtc.example

import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.tange.ai.tirtc.TiRtcCameraFacing
import com.tange.ai.tirtc.TiRtcOutputBufferStrategy
import com.tange.ai.tirtc.TiRtcVideoCodec
import com.tange.ai.tirtc.TiRtcVideoEncoderPreference

internal fun AppCompatActivity.showExampleSettingsPage(
    settings: ExampleSettings,
    onBack: () -> Unit,
    onSave: (ExampleSettings) -> Unit,
) {
    val console =
        CheckBox(this).apply {
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
    val localAudioCodec = spinner(listOf("G711A", "AAC", "PCM"), localAudioCodecIndex(settings.localAudioCodec))
    val localAudioSampleRate =
        spinner(listOf("8 kHz", "16 kHz"), localAudioSampleRateIndex(settings.localAudioSampleRate))
    val localAudioStreamId = editText("localAudio stream id", settings.localAudioStreamId.toString())
    val localAudioAec =
        CheckBox(this).apply {
            text = "AEC"
            isChecked = settings.localAudioAecEnabled
            setTextColor(ExampleTheme.textPrimary)
        }
    val localAudioAgc =
        spinner(
            listOf("Disabled", "Low", "Medium", "High"),
            settings.localAudioAgcLevel.coerceIn(0, 3),
        )
    val localAudioAns =
        spinner(
            listOf("Disabled", "Low", "Medium", "High"),
            settings.localAudioAnsLevel.coerceIn(0, 3),
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
            navigationHeader("偏好设置", onBack)
            addView(sectionTitle("Client"))
            addView(spinnerBlock("Decoder preference", decoder))
            addView(spinnerBlock("Output buffer policy", buffer))
            addView(sectionTitle("Client localAudio"))
            addView(spinnerBlock("audio codec", localAudioCodec))
            addView(spinnerBlock("audio sample rate", localAudioSampleRate))
            addViewWithMargin(fieldBlock("localAudio stream id", localAudioStreamId), bottom = 16)
            addView(surface { addView(localAudioAec) })
            addView(spinnerBlock("AGC", localAudioAgc))
            addView(spinnerBlock("ANS", localAudioAns))
            addView(sectionTitle("Device"))
            addView(spinnerBlock("Camera facing", facing))
            addView(spinnerBlock("Video codec", codec))
            addView(spinnerBlock("Encoder preference", encoder))
            addView(sectionTitle("Logging"))
            addView(surface { addView(console) })
            addView(
                primaryButton("保存") {
                    onSave(
                        ExampleSettings(
                            decoderPreference = DecoderPreference.values()[decoder.selectedItemPosition],
                            outputBufferStrategy =
                                if (buffer.selectedItemPosition == 1) {
                                    TiRtcOutputBufferStrategy.NO_BUFFER
                                } else {
                                    TiRtcOutputBufferStrategy.AUTOMATIC
                                },
                            localAudioCodec = localAudioCodecFromIndex(localAudioCodec.selectedItemPosition),
                            localAudioSampleRate = localAudioSampleRateFromIndex(localAudioSampleRate.selectedItemPosition),
                            localAudioStreamId = localAudioStreamId.text.toString().toIntOrNull() ?: 14,
                            localAudioAecEnabled = localAudioAec.isChecked,
                            localAudioAgcLevel = localAudioProcessingLevelFromIndex(localAudioAgc.selectedItemPosition),
                            localAudioAnsLevel = localAudioProcessingLevelFromIndex(localAudioAns.selectedItemPosition),
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
                        ),
                    )
                },
            )
        },
    )
}
