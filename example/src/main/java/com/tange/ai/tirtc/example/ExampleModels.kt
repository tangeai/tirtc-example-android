package com.tange.ai.tirtc.example

import com.tange.ai.tirtc.TiRtcCameraFacing
import com.tange.ai.tirtc.TiRtcOutputBufferStrategy
import com.tange.ai.tirtc.TiRtcVideoCodec
import com.tange.ai.tirtc.TiRtcVideoEncoderPreference

data class ClientConfiguration(
    val appId: String,
    val endpoint: String,
    val remoteId: String,
    val audioStreamId: Int,
    val videoStreamId: Int,
    val token: String,
)

data class DeviceConfiguration(
    val endpoint: String,
    val deviceId: String,
    val deviceSecretKey: String,
)

data class ExampleSettings(
    val decoderPreference: DecoderPreference = DecoderPreference.AUTO,
    val outputBufferStrategy: TiRtcOutputBufferStrategy = TiRtcOutputBufferStrategy.AUTOMATIC,
    val cameraFacing: TiRtcCameraFacing = TiRtcCameraFacing.FRONT,
    val videoCodec: TiRtcVideoCodec = TiRtcVideoCodec.H264,
    val encoderPreference: TiRtcVideoEncoderPreference = TiRtcVideoEncoderPreference.AUTO,
    val consoleLogEnabled: Boolean = true,
)

enum class DecoderPreference(
    val nativeValue: Int,
    val label: String,
) {
    AUTO(0, "Auto"),
    SOFTWARE(1, "Software"),
    HARDWARE(2, "Hardware"),
}
