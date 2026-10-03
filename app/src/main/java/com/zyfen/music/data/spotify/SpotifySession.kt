package com.zyfen.music.data.spotify

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TotpConfig(val secret: ByteArray, val period: Int, val digits: Int, val version: Int)

/**
 * Anonymous Spotify web session: no developer account, no Client ID/Secret.
 * Uses the same TOTP-protected token flow as Spotify's own web player.
 */
class SpotifySession(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "ZyfenSession"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        // Secrets published by open-source Spotify web clients (ASCII digit strings).
        val CONFIGS = listOf(
            TotpConfig(
                byteArrayOf(
                    55, 48, 49, 48, 51, 55, 56, 49, 49, 57, 56, 55, 55, 57, 51, 51, 57, 48, 55, 57,
                    52, 56, 52, 49, 51, 54, 56, 51, 56, 49, 55, 53, 55, 55, 57, 57, 51, 55, 54, 52,
                    57, 50, 55, 52, 55, 51
                ), 30, 6, 60
            ),
            TotpConfig(
                byteArrayOf(
                    51, 55, 54, 49, 51, 54, 51, 56, 55, 53, 51, 56, 52, 53, 57, 56, 57, 51, 56, 56,
                    51, 51, 49, 50, 51, 49, 48, 57, 49, 49, 57, 57, 50, 56, 52, 55, 49, 49, 50, 52,
                    52, 56, 56, 57, 52, 52, 49, 48, 50, 49, 48, 53, 49, 49, 50, 57, 55, 49, 48, 56
                ), 30, 6, 61
            )
        )
    }

    @Volatile private var accessToken: String? = null
    @Volatile private var clientToken: String? = null
    @Volatile private var fetchedAt = 0L

    fun authHeader(): String? = accessToken?.let { "Bearer $it" }
    fun clientTokenHeader(): String? = clientToken

    fun invalidate() { accessToken = null; clientToken = null; fetchedAt = 0L }

    suspend fun ensure(force: Boolean = false) = withContext(Dispatchers.IO) {
        val fresh = System.currentTimeMillis() - fetchedAt < 20 * 60_000L
        if (!force && fresh && accessToken != null) return@withContext
        var lastError: Exception? = null
        for (cfg in CONFIGS) {
            try {
                val totp = generateTotp(cfg)
                val url = "https://open.spotify.com/api/token?reason=init&productType=web-player" +
                    "&totp=$totp&totpServer=$totp&totpVer=${cfg.version}"
                val req = Request.Builder().url(url)
                    .header("Referer", "https://open.spotify.com/")
                    .header("User-Agent", UA)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val body = runCatching { resp.body?.string() }.getOrNull()?.take(300)
                        Log.w(TAG, "token HTTP ${resp.code} (ver=${cfg.version}): $body")
                        throw IllegalStateException("token HTTP ${resp.code}")
                    }
                    val json = JSONObject(resp.body!!.string())
                    val at = json.getString("accessToken")
                    val cid = json.getString("clientId")
                    Log.i(TAG, "token ok ver=${cfg.version} clientId=$cid")
                    val ct = runCatching { fetchClientToken(cid) }
                        .onFailure { Log.w(TAG, "clienttoken skipped (non-fatal): ${it.message}") }
                        .getOrNull()
                    accessToken = at
                    clientToken = ct
                    fetchedAt = System.currentTimeMillis()
                    return@withContext
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("Spotify session failed: ${lastError?.message}")
    }

    private fun fetchClientToken(clientId: String): String {
        val body = JSONObject()
            .put(
                "client_data",
                JSONObject()
                    .put("client_version", "1.2.91.72.g5337566e")
                    .put("client_id", clientId)
                    .put(
                        "js_sdk_data",
                        JSONObject()
                            .put("device_brand", "unknown")
                            .put("device_model", "unknown")
                            .put("os", "windows")
                            .put("os_version", "NT 10.0")
                            .put("device_id", "325e4218-3239-4c14-9d62-39d4919b1570")
                            .put("device_type", "computer")
                    )
            )
            .toString()
        val req = Request.Builder()
            .url("https://clienttoken.spotify.com/v1/clienttoken")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = runCatching { resp.body?.string() }.getOrNull()?.take(400)
                Log.w(TAG, "clienttoken HTTP ${resp.code} clientId=$clientId: $err")
                throw IllegalStateException("clienttoken HTTP ${resp.code}")
            }
            val json = JSONObject(resp.body!!.string())
            return json.getJSONObject("granted_token").getString("token")
        }
    }

    private fun generateTotp(cfg: TotpConfig): String {
        val counter = System.currentTimeMillis() / 1000L / cfg.period
        val buf = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(cfg.secret, "HmacSHA1"))
        val hash = mac.doFinal(buf)
        val offset = hash[19].toInt() and 0x0f
        val code = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return String.format("%0${cfg.digits}d", code % 1000000)
    }
}
