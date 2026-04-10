package com.tange.ai.tirtc.examples.client

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.tange.ai.tirtc.examples.client.databinding.ActivityScannerBinding
import org.json.JSONObject

internal data class ScanPayload(
  val peerId: String,
  val localId: String,
  val serviceEntry: String,
  val token: String,
)

class ScannerActivity : AppCompatActivity() {
  private lateinit var binding: ActivityScannerBinding
  private var scannerStarted = false

  private val cameraPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (!granted) {
        Toast.makeText(this, R.string.scan_camera_permission_required, Toast.LENGTH_SHORT).show()
        return@registerForActivityResult
      }
      startScanner()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    binding = ActivityScannerBinding.inflate(layoutInflater)
    setContentView(binding.root)
    configureEdgeToEdgeWithPrimaryStatusBar(binding.topAppBarContainer)
    binding.topBar.navigationIcon = null
    installClientLogUploadAction(
      activity = this,
      actionView = binding.topBarUploadAction,
      onStarted = { ClientDemoLogger.info(getString(R.string.log_upload_started)) },
      onSucceeded = { logId -> ClientDemoLogger.info(getString(R.string.log_upload_success, logId)) },
      onFailed = { code, message -> ClientDemoLogger.warn(getString(R.string.log_upload_failed, code, message)) },
    )
    binding.barcodeView.setStatusText(getString(R.string.scan_prompt))

    ensureCameraPermissionThenStart()
  }

  override fun onResume() {
    super.onResume()
    if (!hasCameraPermission()) {
      return
    }
    binding.barcodeView.resume()
    if (!scannerStarted) {
      startScanner()
    }
  }

  override fun onPause() {
    super.onPause()
    if (hasCameraPermission()) {
      binding.barcodeView.pause()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (hasCameraPermission()) {
      binding.barcodeView.pauseAndWait()
    }
  }

  private fun hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }

  private fun ensureCameraPermissionThenStart() {
    if (hasCameraPermission()) {
      startScanner()
      return
    }
    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
  }

  private fun startScanner() {
    if (scannerStarted) {
      return
    }
    scannerStarted = true

    val scanIntent =
      IntentIntegrator(this)
        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        .setPrompt(getString(R.string.scan_prompt))
        .setBeepEnabled(false)
        .setOrientationLocked(true)
        .createScanIntent()

    binding.barcodeView.initializeFromIntent(scanIntent)
    binding.barcodeView.decodeSingle(
      object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
          val payload = parsePayload(result?.text)
          if (payload == null) {
            Toast.makeText(this@ScannerActivity, R.string.scan_invalid, Toast.LENGTH_SHORT).show()
            binding.barcodeView.decodeSingle(this)
            return
          }
          startActivity(ClientActivity.createIntent(this@ScannerActivity, payload))
          finish()
        }
      },
    )
  }

  companion object {
    internal fun parsePayload(raw: String?): ScanPayload? {
      if (raw.isNullOrBlank()) {
        return null
      }
      return try {
        val json = JSONObject(raw.trim())
        val peerId = json.optString("peer_id", "").trim()
        val localId = json.optString("local_id", "").trim()
        val serviceEntry = json.optString("service_entry", "").trim()
        val token = json.optString("token", "").trim()
        if (peerId.isNotEmpty() && token.isNotEmpty()) {
          ScanPayload(
            peerId = peerId,
            localId = localId,
            serviceEntry = serviceEntry,
            token = token,
          )
        } else {
          null
        }
      } catch (_: Exception) {
        null
      }
    }
  }
}
