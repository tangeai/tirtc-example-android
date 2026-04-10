package com.tange.ai.tirtc.examples.server

internal enum class ServerSessionState {
  READY,
  CONNECTING,
  CONNECTED,
  DISCONNECTED,
}

internal data class ServerConfig(
  val license: String,
  val serviceEntry: String,
)

internal const val DEFAULT_VIDEO_WIDTH = 1280
internal const val DEFAULT_VIDEO_HEIGHT = 1280
internal const val DEFAULT_VIDEO_FPS = 24
internal const val DEFAULT_VIDEO_BITRATE_KBPS = 0
internal const val STREAM_MESSAGE_INITIAL_DELAY_MS = 1_000L
internal const val STREAM_MESSAGE_INTERVAL_MS = 10_000L
