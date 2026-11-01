package com.familyshield.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import com.familyshield.app.BuildConfig
import com.familyshield.app.lists.ListKind
import com.familyshield.app.lists.ListSources
import com.familyshield.app.lists.NetworkRule
import com.familyshield.app.shield
import com.familyshield.app.update.SelfUpdater
import com.familyshield.app.vpn.ShieldVpnService
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var onButton: Button
    private lateinit var count: TextView
    private lateinit var problems: TextView
    private lateinit var privateDnsButton: Button
    private lateinit var refreshButton: Button
    private lateinit var refreshNote: TextView
    private lateinit var recent: TextView
    private lateinit var lists: TextView
    private lateinit var updateSwitchLine: TextView
    private lateinit var installButton: Button
    private lateinit var update: TextView

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
        c.addView(Ui.title(this, "광고 방패"))
        // 전화로 "제목 밑에 버전 몇이라고 써 있어요?" 하고 바로 확인할 수 있게 맨 위에 둔다 (새버전_설치방법.md)
        c.addView(Ui.body(this, "버전 ${BuildConfig.VERSION_NAME}", size = 20f).apply { setTextColor(Ui.GRAY) })
        updateSwitchLine = Ui.body(this, size = 20f).also { c.addView(it) }

        status = Ui.body(this, size = 28f).also { c.addView(it) }
        onButton = Ui.button(this, "보호 켜기") { turnOn() }.also { c.addView(it) }
        count = Ui.body(this, size = 22f).also { c.addView(it) }
        problems = Ui.body(this, size = 21f).apply { setTextColor(Ui.RED) }.also { c.addView(it) }
        privateDnsButton = Ui.button(this, "비공개 DNS 설정 열기") {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }.also { c.addView(it) }

        installButton = Ui.button(this, "새 버전 설치하기") {
            shield.pendingInstall?.let { startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }.also { c.addView(it) }
        refreshButton = Ui.button(this, "지금 새로고침") { refreshNow() }.also { c.addView(it) }
        refreshNote = Ui.body(this, size = 18f).apply { setTextColor(Ui.GRAY) }.also { c.addView(it) }

        c.addView(Ui.heading(this, "최근 막은 주소"))
        recent = Ui.body(this, size = 18f).also { c.addView(it) }

        c.addView(Ui.heading(this, "차단 목록 상태"))
        lists = Ui.body(this, size = 17f).also { c.addView(it) }

        c.addView(Ui.heading(this, "앱 업데이트"))
        update = Ui.body(this, size = 17f).also { c.addView(it) }

        c.addView(Ui.button(this, "처음 설정 도우미") { startActivity(Intent(this, SetupActivity::class.java)) })
        c.addView(Ui.button(this, "보호 끄기") { confirmTurnOff() })

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
        val s = shield
        if (s.vpnRunning) {
            status.text = "✅ 보호 중"
            status.setTextColor(Ui.GREEN)
        } else {
            status.text = "⚠️ 보호가 꺼져 있습니다"
            status.setTextColor(Ui.RED)
        }
        Ui.show(onButton, !s.vpnRunning)
        count.text = "지금까지 막은 광고·위험 주소: %,d번\n(차단 규칙 %,d개 사용 중, 불러오기 %.1f초)".format(s.blockLog.total, s.filterSize, s.filterBuildMs / 1000.0)

        val p = mutableListOf<String>()
        s.vpnProblem?.let { p += "⚠ $it" }
        s.dnsProblem?.let { if (s.vpnRunning) p += "⚠ $it" }
        if (s.privateDnsStrict) {
            p += "⚠ '비공개 DNS'(개인 DNS)가 직접 지정으로 켜져 있어 광고가 막히지 않습니다. " +
                "아래 버튼으로 설정을 열고, 'DNS'를 검색해 '자동' 또는 '사용 안 함'으로 바꿔 주세요."
        }
        if (s.prefs.lockdown) p += "⚠ 'VPN 없는 연결 차단'이 켜져 있습니다. 이 앱이 멈추면 인터넷이 끊기니 꺼 주세요."
        val blockLists = s.allLists.filter { it.kind != ListKind.SETTING }
        val failedLists = blockLists.count { s.store.status(it.id).error != null }
        if (failedLists > 0) p += "⚠ 차단 목록 ${failedLists}개를 받지 못했습니다 (아래 '차단 목록 상태' 참고). 예전에 받은 목록(없으면 앱에 들어 있는 기본 목록)으로 계속 막고 있습니다."
        problems.text = p.joinToString("\n\n")
        Ui.show(problems, p.isNotEmpty())
        Ui.show(privateDnsButton, s.privateDnsStrict)

        refreshButton.isEnabled = !s.refreshing
        refreshButton.text = if (s.refreshing) "새로고침 중…" else "지금 새로고침"

        val entries = s.blockLog.recent().take(15)
        recent.text = if (entries.isEmpty()) "아직 없습니다" else entries.joinToString("\n") { "${Ui.clock(it.time)}  ${it.name}" }

        lists.text = blockLists.joinToString("\n\n") { src ->
            val st = s.store.status(src.id)
            val head = src.label + (st.entries?.let { " — %,d개".format(it) } ?: "")
            val got = st.lastSuccess?.let { "받은 때: ${Ui.time(it)}" }
                ?: if (src.network == NetworkRule.ANY) "아직 안 받음 (앱에 들어 있는 기본 목록 사용 중)" else "아직 안 받음 (와이파이 연결 때 받음)"
            val err = st.error?.let { "\n⚠ $it" } ?: ""
            "$head\n$got$err"
        }

        val updateOn = s.selfUpdateOn()
        updateSwitchLine.text = if (updateOn) "자동 업데이트: 켜짐" else "자동 업데이트: 꺼짐"
        updateSwitchLine.setTextColor(if (updateOn) Ui.GREEN else Ui.GRAY)
        val switchError = s.store.status(ListSources.UPDATE_SWITCH_ID).error?.let { "\n⚠ 스위치 받기: $it" } ?: ""
        update.text = if (updateOn) {
            val checked = s.prefs.updateCheckedAt.takeIf { it > 0 }?.let { " (${Ui.time(it)} 확인)" } ?: ""
            (s.prefs.updateStatus ?: "아직 확인 안 함") + checked + switchError
        } else {
            "꺼져 있습니다. 새 버전은 오너가 직접 설치합니다.\n(켜고 끄기: GitHub 의 lists/AUTO_UPDATE.txt)$switchError"
        }
        Ui.show(installButton, s.pendingInstall != null)

    }

    private fun refreshNow() {
        val s = shield
        refreshNote.text = if (s.isUnmetered()) "목록을 확인하는 중입니다 (1분 정도)" else
            "모바일 데이터라서 큰 목록은 와이파이에서 받습니다. 작은 목록만 확인합니다."
        refreshButton.isEnabled = false
        thread(name = "manual-refresh") {
            s.refreshLists(s.allLists)
            val result = SelfUpdater(this).checkAndInstall() // 자동 업데이트가 꺼져 있으면 아무 요청 없이 끝남
            runOnUiThread { refreshNote.text = "새로고침 끝 — $result" }
        }
    }

    private fun turnOn() {
        val consent = VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, REQ_VPN) else ShieldVpnService.start(this)
    }

    @Deprecated("Activity 결과 API (프레임워크만 사용)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) ShieldVpnService.start(this)
    }

    private fun confirmTurnOff() {
        AlertDialog.Builder(this)
            .setTitle("보호를 끌까요?")
            .setMessage("끄면 광고와 사기 사이트가 다시 보입니다.")
            .setPositiveButton("끄기") { _, _ -> ShieldVpnService.stop(this) }
            .setNegativeButton("취소", null)
            .show()
    }

    companion object {
        const val REQ_VPN = 1
    }
}
