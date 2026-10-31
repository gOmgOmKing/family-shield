package com.familyshield.app.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.familyshield.app.Notifications
import com.familyshield.app.shield
import com.familyshield.app.ui.MainActivity
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * 폰 안에만 있는 VPN. 가짜 DNS 주소 두 개(IPv4·IPv6)로 가는 패킷만 받아서 검사하고,
 * 나머지 인터넷은 VPN 을 거치지 않는다. 그래서 데이터 사용량이 늘지 않고, 방문 기록이 밖으로 나가지 않는다.
 */
class ShieldVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var wakePipe: Array<FileDescriptor>? = null
    private var readerThread: Thread? = null
    private var forwarder: DnsForwarder? = null
    private var upstream: UpstreamDns? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shield.prefs.protectionWanted = false
            stopVpn()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        shield.prefs.protectionWanted = true
        goForeground()
        startVpn()
        return START_STICKY
    }

    private fun goForeground() {
        val n = Notifications.protecting(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Play 스토어 심사용 구분이라, 직접 설치하는 이 앱은 어느 폰에서나 되는 specialUse 를 쓴다
            startForeground(Notifications.ID_PROTECTING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_PROTECTING, n)
        }
    }

    @Synchronized
    private fun startVpn() {
        if (tun != null) return
        val shield = shield
        if (prepare(this) != null) {
            fail("VPN 허락이 필요합니다. 앱에서 '보호 켜기'를 눌러 주세요.")
            return
        }

        val up = UpstreamDns(this) { strict -> shield.privateDnsStrict = strict }
        up.start()

        val iface = try {
            Builder()
                .setSession("광고 방패")
                .addAddress(TUN_V4, 32)
                .addAddress(TUN_V6, 128)
                .addDnsServer(FAKE_DNS_V4)
                .addRoute(FAKE_DNS_V4, 32)
                .addDnsServer(FAKE_DNS_V6)
                .addRoute(FAKE_DNS_V6, 128)
                .addDisallowedApplication(packageName)
                .setBlocking(true)
                .setMtu(1500)
                .setConfigureIntent(
                    PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                )
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false) }
                .establish()
        } catch (e: Exception) {
            null
        }
        if (iface == null) {
            up.stop()
            fail("VPN 을 만들지 못했습니다. 다른 VPN 앱이 켜져 있는지 확인해 주세요.")
            return
        }

        val out = FileOutputStream(iface.fileDescriptor)
        val write: (ByteArray) -> Unit = { packet ->
            synchronized(out) {
                try {
                    out.write(packet)
                } catch (e: IOException) {
                    // 통로가 닫히는 중
                }
            }
        }
        val fwd = DnsForwarder(up, write) { problem -> shield.dnsProblem = problem }
        val pipe = Os.pipe()

        tun = iface
        wakePipe = pipe
        upstream = up
        forwarder = fwd
        readerThread = thread(name = "dns-reader") { readLoop(iface.fileDescriptor, pipe[0], write, fwd) }

        shield.vpnRunning = true
        shield.vpnProblem = null
        Notifications.clearProblem(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            shield.prefs.alwaysOn = isAlwaysOn
            shield.prefs.lockdown = isLockdownEnabled
        }
    }

    private fun readLoop(tunFd: FileDescriptor, wakeFd: FileDescriptor, write: (ByteArray) -> Unit, fwd: DnsForwarder) {
        val shield = shield
        val interceptor = DnsInterceptor { shield.filter }
        val buf = ByteArray(32_767)
        val fds = arrayOf(
            StructPollfd().apply { fd = tunFd; events = OsConstants.POLLIN.toShort() },
            StructPollfd().apply { fd = wakeFd; events = OsConstants.POLLIN.toShort() },
        )
        var sinceSave = 0
        try {
            while (true) {
                fds.forEach { it.revents = 0 }
                try {
                    Os.poll(fds, -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    throw e
                }
                if (fds[1].revents.toInt() != 0) break // 멈추라는 신호
                if (fds[0].revents.toInt() and OsConstants.POLLIN == 0) {
                    if (fds[0].revents.toInt() != 0) break // 통로 오류
                    continue
                }
                val n = Os.read(tunFd, buf, 0, buf.size)
                if (n <= 0) continue
                when (val v = interceptor.inspect(buf, n)) {
                    is Verdict.Block -> {
                        write(v.reply)
                        shield.blockLog.record(v.name, System.currentTimeMillis())
                        if (++sinceSave >= 20) {
                            shield.saveBlockedCount()
                            sinceSave = 0
                        }
                    }
                    is Verdict.Forward -> fwd.submit(v)
                    Verdict.Ignore -> Unit
                }
            }
        } catch (e: ErrnoException) {
            // 통로가 닫힘
        } catch (e: IOException) {
            // 통로가 닫힘
        }
    }

    @Synchronized
    private fun stopVpn() {
        val shield = shield
        wakePipe?.let { pipe ->
            try {
                Os.write(pipe[1], byteArrayOf(1), 0, 1)
            } catch (e: Exception) {
                // 이미 닫힘
            }
        }
        readerThread?.join(2000)
        readerThread = null
        forwarder?.shutdown()
        forwarder = null
        upstream?.stop()
        upstream = null
        tun?.close()
        tun = null
        wakePipe?.forEach { closeQuietly(it) }
        wakePipe = null
        shield.vpnRunning = false
        shield.saveBlockedCount()
    }

    private fun closeQuietly(fd: FileDescriptor) {
        try {
            Os.close(fd)
        } catch (e: ErrnoException) {
            // 이미 닫힘
        }
    }

    private fun fail(message: String) {
        shield.vpnProblem = message
        shield.vpnRunning = false
        Notifications.problem(this, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 다른 VPN 앱이 켜졌거나, 설정에서 VPN 을 끊었을 때 */
    override fun onRevoke() {
        stopVpn()
        fail("다른 VPN 이 켜졌거나 설정에서 연결이 끊겨 보호가 멈췄습니다.")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.familyshield.app.STOP"

        // 실제 망과 겹치지 않을 사설 주소 (VPN 통로 안에서만 의미 있음)
        private const val TUN_V4 = "10.111.222.1"
        private const val FAKE_DNS_V4 = "10.111.222.2"
        private const val TUN_V6 = "fd00:6a7e:5e1d::1"
        private const val FAKE_DNS_V6 = "fd00:6a7e:5e1d::2"

        private val fakeDns = listOf(InetAddress.getByName(FAKE_DNS_V4), InetAddress.getByName(FAKE_DNS_V6))

        fun isOurFakeDns(address: InetAddress) = address in fakeDns

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ShieldVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ShieldVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
