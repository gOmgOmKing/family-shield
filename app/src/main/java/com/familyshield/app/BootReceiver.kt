package com.familyshield.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.familyshield.app.vpn.ShieldVpnService

/** 폰을 켰을 때, 그리고 앱이 새 버전으로 바뀐 직후에 보호를 다시 켠다 (상시 VPN 설정이 안 되어 있어도) */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val shield = context.shield
        if (!shield.prefs.protectionWanted) return
        if (VpnService.prepare(context) != null) {
            Notifications.problem(context, "VPN 허락이 풀렸습니다.")
            return
        }
        try {
            context.startForegroundService(Intent(context, ShieldVpnService::class.java))
        } catch (e: IllegalStateException) {
            Notifications.problem(context, "폰이 켜질 때 자동으로 시작하지 못했습니다.")
        }
    }
}
