package com.caseforge.scanner.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object LanSelfTest {

    /** Health check against the tablet's LAN IP (not loopback — avoids cleartext loopback quirks). */
    suspend fun healthCheck(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("http://$host:$port/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
            }
            try {
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText().trim()
                if (code == 200 && body == "ok") {
                    "PASS — server responded $code $body at $host:$port"
                } else {
                    error("HTTP $code body=$body")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
