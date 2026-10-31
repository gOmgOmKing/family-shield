import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서명 열쇠 정보는 저장소 밖 파일에서만 읽는다 (docs/절대준수.md §4)
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// 버전은 version.properties 한 곳에서만 (scripts/release.sh 가 올린다)
val appVersionCode = (findProperty("shield.testVersionCode") as String?)?.toInt() // 업데이트 시험 빌드에서만 덮어씀
    ?: Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }.getProperty("versionCode").toInt()

// 새 버전을 받아 오는 곳. 실제 배포(release)는 항상 GitHub Releases.
// 시험 빌드(debug)만 -Pshield.testUpdateBaseUrl=http://127.0.0.1:포트 로 PC 를 가리킬 수 있다 (업데이트 시험용, docs/이력.md #2)
val githubRepo = property("shield.githubRepo") as String
val releaseUpdateBaseUrl = "https://github.com/$githubRepo/releases/latest/download"
val testUpdateBaseUrl = (findProperty("shield.testUpdateBaseUrl") as String?) ?: releaseUpdateBaseUrl

android {
    namespace = "com.familyshield.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.familyshield.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "1.$appVersionCode"
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "UPDATE_BASE_URL", "\"$releaseUpdateBaseUrl\"")
        }
        // 시험 빌드: 이름('광고 방패 시험')·앱 ID 가 달라 진짜 앱과 따로 깔리고, 서명(디버그 열쇠)도 달라 진짜 앱을 덮어쓸 수 없다
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "UPDATE_BASE_URL", "\"$testUpdateBaseUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 저장소의 lists/ 폴더를 앱 안에도 그대로 넣어, 처음 켜자마자 기본 차단이 동작하게 한다
    sourceSets["main"].assets.srcDir(rootProject.file("lists"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
