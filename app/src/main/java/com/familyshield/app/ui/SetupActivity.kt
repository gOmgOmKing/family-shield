package com.familyshield.app.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import com.familyshield.app.shield
import com.familyshield.app.vpn.ShieldVpnService

/** 처음 설정 도우미: 필요한 설정 화면을 하나씩 버튼으로 연다. 돌아오면 ✅ 표시가 바뀐다 */
class SetupActivity : Activity() {

    /** isDone 이 null 이면 unknownText 를 보여 준다 (확인할 수 없거나, 지금은 필요 없는 단계) */
    private class Step(val status: TextView, val isDone: () -> Boolean?, val unknownText: String)

    private val steps = mutableListOf<Step>()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = Ui.column(this)
        c.addView(Ui.title(this, "처음 설정 도우미"))
        c.addView(Ui.body(this, "위에서부터 차례로 버튼을 눌러 주세요. 다 되면 ✅ 로 바뀝니다."))

        fun step(
            title: String, explain: String, button: String, isDone: () -> Boolean?,
            unknownText: String = "❔ 이 폰에서는 확인할 수 없음 (보호를 켠 뒤 다시 보세요)",
            action: () -> Unit,
        ) {
            c.addView(Ui.heading(this, title))
            val status = Ui.body(this, size = 21f).also { c.addView(it) }
            c.addView(Ui.body(this, explain, size = 18f))
            c.addView(Ui.button(this, button, action))
            steps += Step(status, isDone, unknownText)
        }

        step(
            "① 보호 허락", "'연결 요청' 창이 뜨면 '확인'을 누르세요. 폰 안에서만 동작하며 방문 기록은 밖으로 나가지 않습니다.",
            "허락하고 켜기", { VpnService.prepare(this) == null && shield.vpnRunning },
        ) {
            val consent = VpnService.prepare(this)
            if (consent != null) startActivityForResult(consent, MainActivity.REQ_VPN) else ShieldVpnService.start(this)
        }

        step(
            "② 알림 허락", "보호가 꺼지거나 새 버전이 나왔을 때 알려 드립니다.",
            "알림 허락하기", { notificationsAllowed() },
        ) { askNotifications() }

        step(
            "③ 배터리 절약에서 빼기", "폰이 배터리를 아끼느라 보호를 멈추지 않게 합니다. '허용'을 누르세요.",
            "배터리 설정 열기", { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) },
        ) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        step(
            "④ 항상 켜기 (상시 VPN)",
            "열리는 화면에서 '광고 방패' 옆 톱니바퀴(⚙)를 누르고 '상시 VPN'(항상 켜기)을 켜세요.\n" +
                "그 아래 'VPN 없는 연결 차단'은 켜지 마세요 — 이 앱이 멈추면 인터넷이 끊깁니다.",
            "VPN 설정 열기", { shield.prefs.alwaysOn },
        ) { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }

        step(
            "⑤ 새 버전 설치 허락",
            "자동 업데이트가 켜져 있을 때만 필요합니다. 켜져 있으면 '이 출처 허용'을 켜세요.",
            "설치 허락 화면 열기", { if (shield.selfUpdateOn()) packageManager.canRequestPackageInstalls() else null },
            unknownText = "⏸ 지금은 필요 없음 (자동 업데이트 꺼짐)",
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        }

        step(
            "⑥ 비공개 DNS 확인", "'비공개 DNS'(개인 DNS)가 직접 지정으로 켜져 있으면 광고가 막히지 않습니다. " +
                "설정에서 'DNS'를 검색해 '자동' 또는 '사용 안 함'으로 두세요.",
            "네트워크 설정 열기", { if (shield.vpnRunning) !shield.privateDnsStrict else null },
        ) { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }

        c.addView(Ui.heading(this, "크롬(Chrome) 설정 — 직접 해 주세요"))
        c.addView(
            Ui.body(
                this,
                "크롬 오른쪽 위 ⋮ → 설정 → 사이트 설정 에서:\n" +
                    "• 알림 → 끄기 (가짜 '바이러스 감염' 알림 방지)\n" +
                    "• 팝업 및 리디렉션 → 차단\n" +
                    "• 광고 → '침입 광고 차단' 켜기",
                size = 19f,
            ),
        )

        Ui.setPage(this, c)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun render() {
        for (s in steps) {
            when (s.isDone()) {
                true -> s.status.apply { text = "✅ 완료"; setTextColor(Ui.GREEN) }
                false -> s.status.apply { text = "⬜ 아직 안 됨"; setTextColor(Ui.RED) }
                null -> s.status.apply { text = s.unknownText; setTextColor(Ui.GRAY) }
            }
        }
    }

    private fun notificationsAllowed(): Boolean =
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("setup", MODE_PRIVATE).getBoolean("askedNotif", false)
        ) {
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean("askedNotif", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        render()
    }

    @Deprecated("Activity 결과 API (프레임워크만 사용)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MainActivity.REQ_VPN && resultCode == RESULT_OK) ShieldVpnService.start(this)
        render()
    }

    private companion object {
        const val REQ_NOTIF = 2
    }
}
