package com.familyshield.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.net.InetAddress

/**
 * 지금 폰이 쓰는 실제 망(와이파이/모바일 데이터)과 그 망이 알려 준 DNS 서버.
 * 이 앱은 VPN 에서 빠져 있으므로 "기본 망"은 항상 실제 망이다. 외부(제3자) DNS 는 쓰지 않는다.
 */
class UpstreamDns(context: Context, private val onPrivateDnsStrict: (Boolean) -> Unit) {

    private class Snapshot(val network: Network?, val servers: List<InetAddress>)

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var current = Snapshot(null, emptyList())

    val network: Network? get() = current.network
    val servers: List<InetAddress> get() = current.servers

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            if (cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
            current = Snapshot(network, lp.dnsServers.filterNot { ShieldVpnService.isOurFakeDns(it) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) onPrivateDnsStrict(lp.privateDnsServerName != null)
        }

        override fun onLost(network: Network) {
            if (network == current.network) current = Snapshot(null, emptyList())
        }
    }

    fun start() = cm.registerDefaultNetworkCallback(callback)

    fun stop() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // 이미 해제됨
        }
    }
}
