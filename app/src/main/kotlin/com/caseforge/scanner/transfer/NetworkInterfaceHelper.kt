package com.caseforge.scanner.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Enumerates usable LAN IPv4 addresses for the export server URL display.
 * The HTTP server itself binds on [BIND_ALL] (0.0.0.0).
 */
object NetworkInterfaceHelper {

    const val BIND_ALL = "0.0.0.0"

    data class Candidate(
        val address: String,
        val interfaceName: String,
        val source: String,
    )

    /** All non-loopback IPv4 addresses on up interfaces. */
    fun allIpv4Candidates(): List<Candidate> {
        val out = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (!seen.add(host)) continue
                    out += Candidate(
                        address = host,
                        interfaceName = iface.displayName ?: iface.name,
                        source = "interface",
                    )
                }
            }
        }
        return out.sortedBy { it.address }
    }

    /** Wi‑Fi IPv4 from ConnectivityManager (may match one interface entry). */
    fun wifiIpv4FromConnectivity(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val linkProps = cm.getLinkProperties(network) ?: return null
        for (addr in linkProps.linkAddresses) {
            val ip = addr.address
            if (ip is Inet4Address && !ip.isLoopbackAddress) return ip.hostAddress
        }
        return null
    }

    /**
     * Prefer private RFC1918 Wi‑Fi address, else first private, else first candidate.
     */
    fun pickBest(candidates: List<Candidate>, wifiHint: String?): Candidate? {
        if (candidates.isEmpty()) return null
        wifiHint?.let { w ->
            candidates.firstOrNull { it.address == w }?.let { return it }
        }
        candidates.firstOrNull { isPrivateLan(it.address) }?.let { return it }
        return candidates.first()
    }

    fun isPrivateLan(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when (parts[0]) {
            10 -> true
            172 -> parts[1] in 16..31
            192 -> parts[1] == 168
            else -> false
        }
    }

    fun mergeCandidates(context: Context): List<Candidate> {
        val fromIfaces = allIpv4Candidates()
        val wifi = wifiIpv4FromConnectivity(context)
        if (wifi == null) return fromIfaces
        if (fromIfaces.any { it.address == wifi }) return fromIfaces
        return listOf(Candidate(wifi, "wifi", "connectivity")) + fromIfaces
    }
}
