package com.tange.ai.tirtc.examples.server

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
import com.tange.ai.tirtc.examples.server.databinding.ActivityServerScannerBinding
import org.json.JSONObject

class ServerScannerActivity : AppCompatActivity() {
  private lateinit var binding: ActivityServerScannerBinding
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
    binding = ActivityServerScannerBinding.inflate(layoutInflater)
    setContentView(binding.root)
    configureEdgeToEdgeWithPrimaryStatusBar(binding.topAppBarContainer)
    binding.topBar.navigationIcon = null
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
          val license = parseLicense(result?.text)
          if (license.isNullOrBlank()) {
            Toast.makeText(this@ServerScannerActivity, R.string.scan_invalid, Toast.LENGTH_SHORT).show()
            binding.barcodeView.decodeSingle(this)
            return
          }
          startActivity(ServerActivity.createIntent(this@ServerScannerActivity, license))
          finish()
        }
      },
    )
  }

  companion object {
    fun parseLicense(raw: String?): String? {
      if (raw.isNullOrBlank()) {
        return null
      }
      return try {
        val json = JSONObject(raw.trim())
        json.optString("license", "").trim().takeIf { it.isNotEmpty() }
      } catch (_: Exception) {
        null
      }
    }
  }
}
