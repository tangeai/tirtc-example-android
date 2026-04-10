package com.tange.ai.tirtc.examples.client

import android.app.Application

class ClientExampleApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    val code = ClientRuntimeBootstrap.initializeForApplication(this)
    if (code != 0) {
      ClientDemoLogger.warn("runtime_bootstrap_failed code=$code")
    } else {
      ClientDemoLogger.info("runtime_bootstrap_ok")
    }
  }
}
