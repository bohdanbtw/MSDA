package com.msda.android.steam

import com.google.protobuf.CodedOutputStream
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64

class SteamProtoClient(
    private val client: OkHttpClient
) {
    var lastEResult: Int? = null
        private set

    suspend fun post(route: String, payload: ByteArray): ByteArray {
        val encoded = Base64.getEncoder().encodeToString(payload)
        val body = FormBody.Builder()
            .add("input_protobuf_encoded", encoded)
            .build()
        val request = Request.Builder()
            .url(withProtobufFormat(route))
            .post(body)
            .build()
        return execute(request)
    }

    suspend fun get(route: String, payload: ByteArray): ByteArray {
        val encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(payload), "UTF-8")
        val connector = if (route.contains("?")) "&" else "?"
        val request = Request.Builder()
            .url(withProtobufFormat("$route${connector}input_protobuf_encoded=$encoded"))
            .get()
            .build()
        return execute(request)
    }

    private fun execute(request: Request): ByteArray {
        val response = client.newCall(request).execute()
        val bodyBytes = response.body?.bytes() ?: byteArrayOf()
        if (!response.isSuccessful) {
            throw IllegalStateException(
                "HTTP ${response.code}: ${previewBody(bodyBytes)}"
            )
        }

        val eResult = extractEResult(response.header("x-eresult"), bodyBytes)
        lastEResult = eResult
        val protoBytes = extractProtobufBody(bodyBytes)

        if (eResult != null && eResult != 1) {
            throw IllegalStateException(
                buildSteamFailureMessage(eResult, bodyBytes, protoBytes)
            )
        }
        if (eResult == null && protoBytes.isEmpty()) {
            throw IllegalStateException(
                "Steam API returned empty body without x-eresult header. ${previewBody(bodyBytes)}"
            )
        }

        return protoBytes
    }

    private fun buildSteamFailureMessage(eResult: Int, rawBody: ByteArray, protoBytes: ByteArray): String {
        val base = steamErrorMessage(eResult)
        val preview = previewBody(rawBody)
        return if (protoBytes.isEmpty()) {
            "$base (eResult=$eResult). $preview"
        } else {
            "$base (eResult=$eResult)"
        }
    }

    private fun withProtobufFormat(route: String): String {
        if (route.contains("format=")) return route
        val connector = if (route.contains("?")) "&" else "?"
        return "$route${connector}format=protobuf_raw"
    }

    private fun steamErrorMessage(eResult: Int): String {
        return when (eResult) {
            5 -> "Invalid Steam password"
            63 -> "Invalid Steam Guard code"
            85 -> "Steam Guard code mismatch — check device time sync"
            87 -> "Account login denied"
            else -> "Steam login failed"
        }
    }

    private fun previewBody(body: ByteArray, maxLen: Int = 160): String {
        if (body.isEmpty()) return "body=<empty>"
        val text = String(body, Charsets.UTF_8)
        val snippet = if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."
        return "body=$snippet"
    }
}

internal fun extractEResult(header: String?, body: ByteArray): Int? {
    header?.toIntOrNull()?.let { return it }
    if (body.isEmpty()) return null

    val text = body.toUtf8Trimmed()
    if (!text.startsWith("{")) return null

    return try {
        val json = JSONObject(text)
        when {
            json.has("eresult") -> json.optInt("eresult")
            json.has("result") -> json.optInt("result")
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}

internal fun extractProtobufBody(bodyBytes: ByteArray): ByteArray {
    if (bodyBytes.isEmpty()) return bodyBytes

    val text = bodyBytes.toUtf8Trimmed()
    if (!text.startsWith("{")) return bodyBytes

    return try {
        val json = JSONObject(text)
        when (val response = json.opt("response")) {
            null -> bodyBytes
            is String -> {
                if (response.isBlank()) byteArrayOf()
                else Base64.getDecoder().decode(response)
            }
            is JSONObject -> jsonObjectToProtobuf(response)
            else -> bodyBytes
        }
    } catch (_: Throwable) {
        bodyBytes
    }
}

private fun jsonObjectToProtobuf(obj: JSONObject): ByteArray {
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)

    writeJsonStringField(coded, obj, "publickey_mod", 1)
    writeJsonStringField(coded, obj, "public_key_mod", 1)
    writeJsonStringField(coded, obj, "publickey_exp", 2)
    writeJsonStringField(coded, obj, "public_key_exp", 2)
    writeJsonUInt64Field(coded, obj, "timestamp", 3)

    writeJsonUInt64Field(coded, obj, "client_id", 1)
    writeJsonBase64BytesField(coded, obj, "request_id", 2)
    writeJsonFloatField(coded, obj, "interval", 3)
    writeJsonUInt64Field(coded, obj, "steamid", 5)
    writeJsonUInt64Field(coded, obj, "steam_id", 5)
    writeJsonStringField(coded, obj, "extended_error_message", 8)

    writeJsonStringField(coded, obj, "refresh_token", 26)
    writeJsonStringField(coded, obj, "access_token", 34)

    coded.flush()
    return out.toByteArray()
}

private fun writeJsonStringField(
    coded: CodedOutputStream,
    obj: JSONObject,
    key: String,
    tag: Int
) {
    if (!obj.has(key)) return
    val value = obj.optString(key, "")
    if (value.isNotEmpty()) {
        coded.writeString(tag, value)
    }
}

private fun writeJsonUInt64Field(
    coded: CodedOutputStream,
    obj: JSONObject,
    key: String,
    tag: Int
) {
    if (!obj.has(key)) return
    val value = when (val raw = obj.opt(key)) {
        is Number -> raw.toLong()
        else -> obj.optString(key, "").toLongOrNull() ?: 0L
    }
    if (value > 0L) {
        coded.writeUInt64(tag, value)
    }
}

private fun writeJsonFloatField(
    coded: CodedOutputStream,
    obj: JSONObject,
    key: String,
    tag: Int
) {
    if (!obj.has(key)) return
    val value = obj.optDouble(key, 0.0).toFloat()
    if (value > 0f) {
        coded.writeFloat(tag, value)
    }
}

private fun writeJsonBase64BytesField(
    coded: CodedOutputStream,
    obj: JSONObject,
    key: String,
    tag: Int
) {
    if (!obj.has(key)) return
    val value = obj.optString(key, "")
    if (value.isEmpty()) return
    coded.writeByteArray(tag, Base64.getDecoder().decode(value))
}

private fun ByteArray.toUtf8Trimmed(): String {
    return String(this, Charsets.UTF_8).trimStart()
}
