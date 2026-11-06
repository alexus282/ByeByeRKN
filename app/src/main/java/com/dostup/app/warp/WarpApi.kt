package com.dostup.app.warp

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.amnezia.awg.crypto.KeyPair
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class WarpAccount(
    val id: String,
    val token: String,
    val privateKey: String,
    val peerPublicKey: String,
    val addressV4: String,
    val addressV6: String,
) {
    fun toJson(): String = JSONObject()
        .put("id", id).put("token", token)
        .put("privateKey", privateKey).put("peerPublicKey", peerPublicKey)
        .put("addressV4", addressV4).put("addressV6", addressV6)
        .toString()

    companion object {
        fun fromJson(json: String): WarpAccount = JSONObject(json).run {
            WarpAccount(
                getString("id"), getString("token"), getString("privateKey"),
                getString("peerPublicKey"), getString("addressV4"), getString("addressV6"),
            )
        }
    }
}

/** [httpCode] is set when the server answered, i.e. the network path itself worked. */
class WarpApiException(message: String, cause: Throwable? = null, val httpCode: Int? = null) : IOException(message, cause)

/**
 * Registers a device with Cloudflare's (undocumented) WARP client API, the same calls the
 * official client makes. The private key is generated locally and never leaves the device.
 *
 * Russian ISPs drop the TLS handshake to api.cloudflareclient.com by SNI (case-insensitive,
 * with TCP reassembly), while zero-trust-client.cloudflareclient.com serves the same API and
 * passes. Each host is tried directly first, then with a split ClientHello as a last resort.
 */
class WarpApi(private val hosts: List<String> = DEFAULT_HOSTS) {
    private val direct = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val split = direct.newBuilder()
        .socketFactory(SplitHelloSocketFactory())
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun register(onRetry: () -> Unit = {}): WarpAccount {
        val attempts = hosts.map { it to direct } + hosts.map { it to split }
        var lastError: WarpApiException? = null
        for ((index, attempt) in attempts.withIndex()) {
            val (host, client) = attempt
            if (index > 0) onRetry()
            try {
                return register(client, "https://$host/$API_VERSION/")
            } catch (e: WarpApiException) {
                // The server answered with an error: another route will not change that.
                if (e.httpCode != null) throw e
                Log.i(TAG, "registration via $host (${if (client === split) "split" else "direct"}) failed: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: WarpApiException("no API hosts configured")
    }

    private fun register(client: OkHttpClient, baseUrl: String): WarpAccount {
        val keyPair = KeyPair()
        val registration = call(
            client, baseUrl, "POST", "reg",
            JSONObject()
                .put("install_id", "")
                .put("tos", Instant.now().toString())
                .put("key", keyPair.publicKey.toBase64())
                .put("fcm_token", "")
                .put("type", "ios")
                .put("locale", "en_US"),
        ).getJSONObject("result")
        val id = registration.getString("id")
        val token = registration.getString("token")

        val config = call(client, baseUrl, "PATCH", "reg/$id", JSONObject().put("warp_enabled", true), token)
            .getJSONObject("result").getJSONObject("config")
        val peer = config.getJSONArray("peers").getJSONObject(0)
        val addresses = config.getJSONObject("interface").getJSONObject("addresses")
        return WarpAccount(
            id = id,
            token = token,
            privateKey = keyPair.privateKey.toBase64(),
            peerPublicKey = peer.getString("public_key"),
            addressV4 = addresses.getString("v4"),
            addressV6 = addresses.getString("v6"),
        )
    }

    private fun call(client: OkHttpClient, baseUrl: String, method: String, path: String, body: JSONObject, token: String? = null): JSONObject {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("User-Agent", "CloudflareWarp/1.0")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw WarpApiException("HTTP ${response.code}: ${text.take(200)}", httpCode = response.code)
                }
                return JSONObject(text)
            }
        } catch (e: WarpApiException) {
            throw e
        } catch (e: Exception) {
            throw WarpApiException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    companion object {
        private const val TAG = "Dostup/Warp"
        private const val API_VERSION = "v0i1909051800"
        val DEFAULT_HOSTS = listOf("zero-trust-client.cloudflareclient.com", "api.cloudflareclient.com")
    }
}
