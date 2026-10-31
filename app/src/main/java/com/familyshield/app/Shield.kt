package com.familyshield.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.familyshield.app.filter.DomainFilter
import com.familyshield.app.lists.FilterBuilder
import com.familyshield.app.lists.ListSource
import com.familyshield.app.lists.ListSources
import com.familyshield.app.lists.ListStore
import com.familyshield.app.lists.ListUpdater
import com.familyshield.app.lists.UrlHttp
import com.familyshield.app.update.UpdateSwitch
import com.familyshield.app.vpn.BlockLog
import java.io.File
import java.io.IOException
import java.io.Reader
import java.util.concurrent.Executors
import kotlin.random.Random

class ShieldApp : Application() {
    lateinit var shield: Shield
        private set

    override fun onCreate() {
        super.onCreate()
        shield = Shield(this)
        Notifications.createChannels(this)
        Jobs.scheduleAll(this)
        shield.rebuildFilterInBackground()
    }
}

val Context.shield: Shield get() = (applicationContext as ShieldApp).shield

/** 앱 전체가 함께 쓰는 상태: 설정, 목록, 지금 쓰는 차단 규칙, 막은 기록, 화면에 띄울 문제 */
class Shield(private val app: Context) {

    val prefs = Prefs(app.getSharedPreferences("shield", Context.MODE_PRIVATE))
    val store = ListStore(File(app.filesDir, "lists"))
    val ownLists: List<ListSource> = ListSources.own(BuildConfig.GITHUB_REPO)
    val communityLists: List<ListSource> = ListSources.community
    val allLists: List<ListSource> = ownLists + communityLists
    val http = UrlHttp("FamilyShield/${BuildConfig.VERSION_NAME} (+https://github.com/${BuildConfig.GITHUB_REPO})")
    val blockLog = BlockLog(prefs.blockedTotal)

    @Volatile var filter: DomainFilter = DomainFilter.NONE
        private set
    @Volatile var filterSize: Int = 0
        private set

    // 화면에 그대로 보여 줄 현재 상태 (VPN 서비스가 채운다)
    @Volatile var vpnRunning = false
    @Volatile var vpnProblem: String? = null
    @Volatile var dnsProblem: String? = null
    @Volatile var privateDnsStrict = false
    @Volatile var refreshing = false

    /** 받아 놓고 사용자의 '설치'만 기다리는 새 버전 (안드로이드가 준 설치 창 열기). 앱이 다시 시작되면 사라지고 다음 확인 때 다시 만든다 */
    @Volatile var pendingInstall: android.content.Intent? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val refreshLock = Any()

    fun rebuildFilterInBackground() = worker.execute { rebuildFilter() }

    private fun rebuildFilter() {
        val f = FilterBuilder.build(allLists, store, ::openBundled, prefs.coupangOn)
        filter = f
        filterSize = f.blockSize
    }

    /** 자동 업데이트 스위치 (오너가 GitHub lists/AUTO_UPDATE.txt 로 켜고 끔). 받은 사본이 없으면 앱에 들어 있는 기본값(끄기) */
    fun selfUpdateOn(): Boolean {
        val id = ListSources.UPDATE_SWITCH_ID
        val downloaded = store.file(id)
        val text = if (downloaded.exists()) downloaded.readText(Charsets.UTF_8) else openBundled(id)?.use { it.readText() }
        return text != null && UpdateSwitch.isOn(text)
    }

    private fun openBundled(id: String): Reader? = try {
        app.assets.open("$id.txt").reader(Charsets.UTF_8)
    } catch (e: IOException) {
        null
    }

    /** 목록을 받는다. 오래 걸리므로(요청 사이 2~3초 쉼) 반드시 백그라운드 스레드에서 부른다 */
    fun refreshLists(sources: List<ListSource>): Boolean = synchronized(refreshLock) {
        refreshing = true
        try {
            val updater = ListUpdater(store, http, now = System::currentTimeMillis, pause = { Thread.sleep(2000L + Random.nextLong(1000L)) })
            val changed = updater.update(sources, unmetered = isUnmetered())
            if (changed) rebuildFilter()
            changed
        } finally {
            refreshing = false
        }
    }

    /** 이 앱은 VPN 밖에 있으므로 여기서 보는 망은 실제 와이파이/모바일 데이터다 */
    fun isUnmetered(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        return cm.activeNetwork != null && !cm.isActiveNetworkMetered
    }

    fun saveBlockedCount() {
        prefs.blockedTotal = blockLog.total
    }
}

class Prefs(private val p: SharedPreferences) {
    /** 사용자가 보호를 켜 두었는가 (재부팅·업데이트 뒤 다시 켤지 판단) */
    var protectionWanted: Boolean
        get() = p.getBoolean("protectionWanted", false)
        set(v) = p.edit().putBoolean("protectionWanted", v).apply()

    var coupangOn: Boolean
        get() = p.getBoolean("coupangOn", true)
        set(v) = p.edit().putBoolean("coupangOn", v).apply()

    var blockedTotal: Long
        get() = p.getLong("blockedTotal", 0)
        set(v) = p.edit().putLong("blockedTotal", v).apply()

    var updateStatus: String?
        get() = p.getString("updateStatus", null)
        set(v) = p.edit().putString("updateStatus", v).apply()

    var updateCheckedAt: Long
        get() = p.getLong("updateCheckedAt", 0)
        set(v) = p.edit().putLong("updateCheckedAt", v).apply()

    /** 안드로이드 10 이상에서만 알 수 있음. 모르면 null */
    var alwaysOn: Boolean?
        get() = if (p.contains("alwaysOn")) p.getBoolean("alwaysOn", false) else null
        set(v) = if (v == null) p.edit().remove("alwaysOn").apply() else p.edit().putBoolean("alwaysOn", v).apply()

    var lockdown: Boolean
        get() = p.getBoolean("lockdown", false)
        set(v) = p.edit().putBoolean("lockdown", v).apply()
}
