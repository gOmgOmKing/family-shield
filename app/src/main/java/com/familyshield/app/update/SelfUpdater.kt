package com.familyshield.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.familyshield.app.BuildConfig
import com.familyshield.app.Notifications
import com.familyshield.app.lists.HttpResult
import com.familyshield.app.shield
import java.io.File

/**
 * GitHub Releases 의 최신 릴리스에서 새 버전을 받아 설치한다.
 * 릴리스에는 version.txt(versionCode 숫자)와 family-shield.apk 를 올린다 (scripts/release.sh 가 만든다).
 * 서명이 다른 APK 는 안드로이드가 설치를 거부하므로, GitHub 이 털려도 가짜 앱으로 바뀌지 않는다.
 *
 * 오너가 GitHub 의 lists/AUTO_UPDATE.txt 를 '켜기'로 바꿨을 때만 동작한다 (기본 꺼짐).
 * 꺼 둔 이유: 2026-10-31 실기기 시험에서 Google Play 프로텍트가 스스로 업데이트를 "유해한 앱"으로 막았다 (docs/이력.md #2).
 */
class SelfUpdater(private val context: Context) {

    private val shield = context.shield
    private val base = BuildConfig.UPDATE_BASE_URL

    /** @return 화면에 보여 줄 결과 문장 (같은 문장이 설정에도 저장됨) */
    fun checkAndInstall(): String {
        if (!shield.selfUpdateOn()) return "자동 업데이트 꺼짐 — 새 버전은 오너가 직접 설치합니다"
        val message = try {
            check()
        } catch (e: Exception) {
            "업데이트 중 오류: ${e.javaClass.simpleName}"
        }
        shield.prefs.updateStatus = message
        shield.prefs.updateCheckedAt = System.currentTimeMillis()
        return message
    }

    private fun check(): String {
        val remote = when (val r = shield.http.get("$base/version.txt", etag = null, maxBytes = 64)) {
            is HttpResult.Ok -> ReleaseVersion.parse(String(r.body, Charsets.US_ASCII)) ?: return "새 버전 정보를 읽지 못했습니다"
            else -> return "새 버전 확인 실패: ${describe(r)}"
        }
        if (!ReleaseVersion.isNewer(remote, BuildConfig.VERSION_CODE)) return "최신 버전입니다 (버전 ${BuildConfig.VERSION_CODE})"

        val body = when (val r = shield.http.get("$base/family-shield.apk", etag = null, maxBytes = MAX_APK_BYTES)) {
            is HttpResult.Ok -> r.body
            else -> return "새 버전 받기 실패: ${describe(r)}"
        }
        val apk = File(context.cacheDir, "update.apk").apply { writeBytes(body) }
        verify(apk, remote)?.let { return it }
        install(apk)
        return "새 버전($remote) 설치를 시작했습니다"
    }

    /** 다른 앱이거나, 버전이 안 맞거나, 서명이 다르면 설치하지 않는다 (서명은 안드로이드도 한 번 더 확인함) */
    private fun verify(apk: File, expectedVersion: Int): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return "받은 파일이 앱 설치 파일이 아닙니다"
        if (archive.packageName != context.packageName) return "받은 파일이 광고 방패가 아닙니다"
        @Suppress("DEPRECATION")
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode.toInt() else archive.versionCode
        if (archiveVersion != expectedVersion) return "받은 파일의 버전이 맞지 않습니다"

        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveSigners = signers(archive)
        if (archiveSigners.isNotEmpty() && archiveSigners != signers(installed)) return "서명이 달라 설치하지 않았습니다 (가짜 파일일 수 있음)"
        return null
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners else info.signatures
        return sigs.orEmpty().map { it.toCharsString() }.toSet()
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // 안드로이드 12 이상: 자기 자신을 고칠 때는 묻지 않고 설치되도록 시도. 안 되면 설치 창으로 물어본다
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("family-shield.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun describe(r: HttpResult): String = when (r) {
        is HttpResult.Failed -> r.message
        is HttpResult.Refused -> "서버가 거절함(${r.code})"
        HttpResult.TooLarge -> "파일이 너무 큼"
        HttpResult.NotModified, is HttpResult.Ok -> "알 수 없는 응답"
    }

    private companion object {
        const val MAX_APK_BYTES = 30_000_000L
    }
}

/**
 * 설치 결과. 안드로이드가 "사용자에게 물어봐야 한다"고 하면(파일로 처음 깐 앱은 보통 그렇다):
 * 앱 화면이 떠 있으면 설치 창을 바로 띄우고, 아니면 알림 + 메인 화면의 '새 버전 설치하기' 버튼으로 알린다.
 * (알림이 꺼져 있어도 앱을 열면 버튼으로 설치할 수 있어야 한다 — 실기기 시험에서 알림 권한이 없어 막혔던 일)
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shield = context.shield
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                shield.pendingInstall = confirm
                shield.prefs.updateStatus = "새 버전이 준비됐습니다. '새 버전 설치하기'를 누른 뒤 '설치'를 눌러 주세요."
                Notifications.installReady(context, confirm)
                try {
                    context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: RuntimeException) {
                    // 앱이 화면에 없으면 안드로이드가 막는다 → 알림과 버튼으로 대신 알림
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                shield.pendingInstall = null
                shield.prefs.updateStatus = "새 버전 설치 완료"
            }
            else -> {
                shield.pendingInstall = null
                shield.prefs.updateStatus = "새 버전 설치 안 됨: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "이유 모름"} (다음 확인 때 다시 시도)"
            }
        }
    }
}
