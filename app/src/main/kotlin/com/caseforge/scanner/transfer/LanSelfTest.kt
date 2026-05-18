package com.caseforge.scanner.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object LanSelfTest {

    suspend fun healthCheckLocalhost(port: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("http://127.0.0.1:$port/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
            }
            try {
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText().trim()
                if (code == 200 && body == "ok") {
                    "PASS — server responded $code $body"
                } else {
                    error("HTTP $code body=$body")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
