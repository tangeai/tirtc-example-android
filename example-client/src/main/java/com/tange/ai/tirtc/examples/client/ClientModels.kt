package com.tange.ai.tirtc.examples.client

internal enum class ClientViewState {
  IDLE,
  CONNECTING,
  CONNECTED,
}

internal enum class ClientPlaybackLoadingState {
  HIDDEN,
  VISIBLE,
}

internal enum class ClientSessionState {
  READY,
  CONNECTING,
  CONNECTED,
  DISCONNECTED,
}
