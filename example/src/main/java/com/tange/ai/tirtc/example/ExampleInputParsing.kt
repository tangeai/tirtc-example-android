package com.tange.ai.tirtc.example

import org.json.JSONObject
import java.nio.charset.StandardCharsets

internal fun parseClientQrPayload(
    payload: String,
    current: ClientConfiguration,
    onError: (String) -> Unit,
): ClientConfiguration? {
    return try {
        val json = JSONObject(payload)
        val appId = json.optString("app_id").trim()
        val remoteId = json.optString("remote_id").trim()
        val token = json.optString("token").trim()
        if (appId.isBlank() || remoteId.isBlank() || token.isBlank()) {
            onError("二维码缺少 app_id、remote_id 或 token")
            null
        } else {
            current.copy(
                appId = appId,
                endpoint = json.optString("endpoint").trim(),
                remoteId = remoteId,
                token = token,
                oneTimeToken = token,
                tokenSource = DemoTokenSource.ONE_TIME,
            )
        }
    } catch (error: Exception) {
        onError("二维码 JSON 无效：${error.message}")
        null
    }
}

internal fun parseDeviceQrPayload(
    payload: String,
    onError: (String) -> Unit,
): DeviceConfiguration? {
    return try {
        val json = JSONObject(payload)
        val allowed = setOf("endpoint", "device_id", "device_secret_key")
        val keys = json.keys().asSequence().toSet()
        if (!allowed.containsAll(keys)) {
            onError("设备端二维码包含未允许字段")
            return null
        }
        val deviceId = json.optString("device_id").trim()
        val secret = json.optString("device_secret_key")
        if (deviceId.isBlank() || secret.isBlank()) {
            onError("二维码缺少 device_id 或 device_secret_key")
            null
        } else {
            DeviceConfiguration(
                endpoint = json.optString("endpoint").trim(),
                deviceId = deviceId,
                deviceSecretKey = secret,
            )
        }
    } catch (error: Exception) {
        onError("二维码 JSON 无效：${error.message}")
        null
    }
}

internal fun parseCommandIdOrNull(
    text: String,
    onError: (String) -> Unit,
): Long? {
    val trimmed = text.trim()
    val value =
        if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
        } else {
            trimmed.toLongOrNull()
        }
    if (value == null || value !in 0..MAX_COMMAND_ID) {
        onError("命令 ID 必须是 32 位无符号整数")
        return null
    }
    return value
}

internal fun parseHexPayloadOrNull(
    text: String,
    onError: (String) -> Unit,
): ByteArray? {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.length % 2 != 0 || !compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        onError("HEX 内容必须是偶数位有效字符")
        return null
    }
    return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

internal fun utf8Payload(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)

internal fun payloadText(payload: ByteArray): String = String(payload, StandardCharsets.UTF_8)

internal fun formatCommandId(command: Long): String = "0x${command.toString(16).uppercase()}"

internal fun maskSecretValue(value: String): String {
    if (value.length <= 4) {
        return "****"
    }
    return "${value.take(2)}****${value.takeLast(2)}"
}

internal const val MAX_COMMAND_ID = 0xFFFF_FFFFL
