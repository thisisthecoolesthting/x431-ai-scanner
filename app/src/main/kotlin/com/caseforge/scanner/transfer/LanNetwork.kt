package com.caseforge.scanner.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

object LanNetwork {
    const val DEFAULT_PORT = 8765

    /** Returns IPv4 on active Wi‑Fi, or null if not on Wi‑Fi. */
    fun wifiIpv4OrNull(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val linkProps = cm.getLinkProperties(network) ?: return null
        for (addr in linkProps.linkAddresses) {
            val ip = addr.address
            if (ip is Inet4Address && !ip.isLoopbackAddress) {
                return ip.hostAddress
            }
        }
        return null
    }

    fun baseUrl(host: String, port: Int = DEFAULT_PORT): String = "http://$host:$port"
}
