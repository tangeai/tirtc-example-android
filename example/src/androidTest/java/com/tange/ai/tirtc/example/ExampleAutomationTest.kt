package com.tange.ai.tirtc.example

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleAutomationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun fillClientConfigurationDirectly() {
        launch()
        setTextById("field_app_id", requireArg("app_id"))
        setTextById("field_endpoint", args.getString("endpoint").orEmpty())
        setTextById("field_remote_id", requireArg("remote_id"))
        setTextById("field_audio_stream_id", args.getString("audio_stream_id") ?: "1")
        setTextById("field_video_stream_id", args.getString("video_stream_id") ?: "2")
        setTextById("field_token", requireArg("token"))
        val action = args.getString("action")
        if (action == "start_client" || action == "start_client_volume_toggle") {
            clickText("进入播放页面")
            if (action == "start_client_volume_toggle") {
                val stopButton = requireObject(By.text("停止播放"), PLAYBACK_READY_TIMEOUT_MS)
                check(stopButton.isEnabled) { "playback control is not enabled" }
                SystemClock.sleep(BASELINE_SETTLE_MS)
                clickText("静音播放")
                SystemClock.sleep(MUTE_HOLD_MS)
                clickText("恢复声音")
                SystemClock.sleep(RECOVERY_SETTLE_MS)
                val requestedHoldMs = args.getString("hold_ms")?.toLongOrNull() ?: DEFAULT_HOLD_MS
                val remainingHoldMs =
                    (requestedHoldMs - BASELINE_SETTLE_MS - MUTE_HOLD_MS - RECOVERY_SETTLE_MS)
                        .coerceAtLeast(0L)
                SystemClock.sleep(remainingHoldMs)
            } else {
                SystemClock.sleep(args.getString("hold_ms")?.toLongOrNull() ?: DEFAULT_HOLD_MS)
            }
            if (args.getString("stop_after_hold") != "false") {
                clickText("停止播放")
                clickDescription("返回")
                assertNotNull(device.wait(Until.findObject(By.text("Ti RTC")), WAIT_TIMEOUT_MS))
            }
        }
    }

    private fun launch() {
        val context = instrumentation.targetContext
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("launch intent missing for ${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        assertNotNull(device.wait(Until.findObject(By.text("Ti RTC")), WAIT_TIMEOUT_MS))
    }

    private fun setTextById(
        idName: String,
        value: String,
    ) {
        val objectId = "${instrumentation.targetContext.packageName}:id/$idName"
        val field = requireObject(By.res(objectId), objectId)
        field.setText(value)
    }

    private fun clickText(text: String) {
        requireObject(By.text(text), text).click()
    }

    private fun clickDescription(description: String) {
        requireObject(By.desc(description), description).click()
    }

    private fun requireObject(
        selector: BySelector,
        description: String,
    ): UiObject2 {
        return requireObject(selector, WAIT_TIMEOUT_MS, description)
    }

    private fun requireObject(
        selector: BySelector,
        timeoutMs: Long,
        description: String = selector.toString(),
    ): UiObject2 {
        return device.wait(Until.findObject(selector), timeoutMs)
            ?: error("UI object not found: $description")
    }

    private fun requireArg(name: String): String {
        return args.getString(name)?.takeIf { it.isNotBlank() } ?: error("missing instrumentation arg: $name")
    }

    private companion object {
        private const val WAIT_TIMEOUT_MS = 5000L
        private const val PLAYBACK_READY_TIMEOUT_MS = 20000L
        private const val BASELINE_SETTLE_MS = 2000L
        private const val MUTE_HOLD_MS = 5000L
        private const val RECOVERY_SETTLE_MS = 2000L
        private const val DEFAULT_HOLD_MS = 25000L
    }
}
