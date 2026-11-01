package com.familyshield.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Play 프로텍트가 '유해한 앱'으로 판정한 원인은 다른 앱 설치 권한이었다 (docs/이력.md #14 — 그 권한만 뺀 빌드는 '안전').
 * 1.3 에서 자동 업데이트와 함께 없앴고, 다시 들어오지 않게 지킨다.
 */
class ManifestTest {

    private val manifest =
        (File("src/main/AndroidManifest.xml").takeIf { it.isFile } ?: File("app/src/main/AndroidManifest.xml")).readText()

    @Test
    fun reads_the_real_app_manifest() {
        assertTrue(manifest.contains("android.net.VpnService"))
    }

    @Test
    fun requests_no_permission_to_install_or_remove_apps() {
        for (p in listOf("REQUEST_INSTALL_PACKAGES", "UPDATE_PACKAGES_WITHOUT_USER_ACTION", "INSTALL_PACKAGES", "REQUEST_DELETE_PACKAGES")) {
            assertFalse("매니페스트에 $p 권한이 있음", manifest.contains("\"android.permission.$p\""))
        }
    }
}
