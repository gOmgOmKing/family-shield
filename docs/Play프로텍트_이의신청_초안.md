# Play 프로텍트 이의 신청 — 초안 (제출 전, 오너가 본인 명의로 직접 제출)

> 2026-11-01 2차 시험: 설치 권한 2개를 뺀 빌드는 구글 검사에서 "안전한 앱으로 판단됨", 권한을 둔 빌드는 "유해한 앱 차단됨" (이력 #14).
> 자동 업데이트를 뺀 1.3 을 내면 이 신청이 필요 없을 수 있다. 1.2 를 계속 쓸 때만 제출을 검토한다.

신청서: https://support.google.com/googleplay/android-developer/contact/protectappeals
이 문서는 **넣을 내용만** 준비한 것이다. 신청서의 칸 이름·필수 항목은 확인하지 않았다(열어 보고 맞는 칸에 나눠 넣기).
연락처(이름·이메일)는 오너가 직접 적는다 — 이 문서와 저장소에는 적지 않는다.

## 넣을 값

| 항목 | 값 |
|---|---|
| 앱 이름 | 광고 방패 (FamilyShield) |
| 패키지 이름 | `com.familyshield.app` |
| 버전 | 1.2 (versionCode 2) |
| 판정받은 APK 파일 SHA-256 | `e8c3d196608c3bb4a32d2bbb9f996b219c93eb255391ac995d4d9f4ad0807c53` |
| 서명 인증서 SHA-256 | `1a7d777d555257e6cf1d7df07f6760bc2b085c398e1b4bcaa0d1e536ae337f67` |
| 파일 받는 곳 | https://github.com/gOmgOmKing/family-shield/releases/download/v1.2/family-shield.apk |
| 소스 코드 | https://github.com/gOmgOmKing/family-shield (공개) |
| 받은 경고 | Google Play 프로텍트 "유해한 앱 차단됨" (Harmful app blocked), 2026-11-01, 갤럭시 S23 FE / 안드로이드 16 |

APK 파일 해시는 `dist/family-shield.apk` 와 GitHub 릴리스 v1.2 의 파일을 각각 계산해 같음을 확인했다(2026-11-01).
**새 버전을 내면 해시가 바뀐다** — 그때는 새 파일로 다시 계산한다.

## 설명문 (영어 — 신청서에 붙여 넣을 것)

> I am the developer of "FamilyShield" (package `com.familyshield.app`), a small open-source ad and scam blocker that I built for my own family's two Android phones. It is not distributed on Google Play or anywhere else; I install it on my parents' phones myself. The full source code is public at https://github.com/gOmgOmKing/family-shield.
>
> Google Play Protect shows "Harmful app blocked" for version 1.2 (APK SHA-256 `e8c3d196608c3bb4a32d2bbb9f996b219c93eb255391ac995d4d9f4ad0807c53`, signing certificate SHA-256 `1a7d777d555257e6cf1d7df07f6760bc2b085c398e1b4bcaa0d1e536ae337f67`). I believe this is a false positive and ask you to review it.
>
> What the app does:
> - It uses Android's VpnService only as a local DNS filter. The tunnel routes only a private DNS address; all other traffic does not enter the tunnel. The app reads DNS questions, answers "domain not found" for domains on public blocklists (HaGeZi DNS blocklists, List-KR) and on my own small lists, and forwards every other DNS question to the DNS server of the phone's current network. It does not inspect or modify any other traffic.
> - It does not collect, store, or send personal data. There are no analytics, no ads, and no accounts. The only network requests it makes on its own are downloading the blocklist text files from raw.githubusercontent.com and cdn.jsdelivr.net (with ETag caching, at most once a day for large lists).
>
> Why it requests each sensitive permission:
> - `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: to keep the local DNS filter running so protection does not silently stop.
> - `RECEIVE_BOOT_COMPLETED`: to turn protection back on after the phone restarts.
> - `REQUEST_INSTALL_PACKAGES` and `UPDATE_PACKAGES_WITHOUT_USER_ACTION`: only to update this same app from its own GitHub Releases page. Before installing, the app checks that the downloaded file has the same package name, a higher version number, and the same signing certificate. It never installs any other app. This feature is switched off by default (the switch is a text file in the repository), and it is currently off.
>
> Thank you for reviewing this.

## 설명문 (한국어 번역 — 오너 확인용, 제출은 영어로)

> 저는 "광고 방패(FamilyShield)"(패키지 `com.familyshield.app`)의 개발자입니다. 우리 가족 안드로이드 폰 두 대를 위해 만든 작은 오픈소스 광고·사기 차단 앱입니다. Google Play 를 포함해 어디에도 배포하지 않고, 부모님 폰에 제가 직접 설치합니다. 전체 소스 코드는 https://github.com/gOmgOmKing/family-shield 에 공개되어 있습니다.
>
> 버전 1.2 (APK SHA-256 `e8c3…7c53`, 서명 인증서 SHA-256 `1a7d…7f67`)에 Google Play 프로텍트가 "유해한 앱 차단됨"을 표시합니다. 잘못된 판정으로 생각하여 검토를 요청합니다.
>
> 앱이 하는 일:
> - 안드로이드 VpnService 를 폰 안의 DNS 필터로만 씁니다. 통로에는 사설 DNS 주소 하나만 들어오고, 다른 통신은 통로에 들어오지 않습니다. 앱은 DNS 질문을 읽어 공개 차단 목록(HaGeZi, List-KR)과 제 작은 목록에 있는 주소에는 "없는 주소"로 답하고, 나머지 질문은 지금 연결된 망의 DNS 서버로 그대로 넘깁니다. 그 밖의 통신은 보거나 바꾸지 않습니다.
> - 개인정보를 모으거나 저장하거나 보내지 않습니다. 분석 도구·광고·계정이 없습니다. 앱이 스스로 하는 접속은 raw.githubusercontent.com 과 cdn.jsdelivr.net 에서 차단 목록 텍스트 파일을 받는 것뿐입니다(ETag 사용, 큰 목록은 하루 한 번까지).
>
> 민감한 권한을 쓰는 이유:
> - 포그라운드 서비스 권한과 배터리 최적화 제외 요청: 폰 안 DNS 필터가 계속 돌아 보호가 조용히 멈추지 않게 하려고.
> - 부팅 완료 수신: 폰을 다시 켠 뒤 보호를 다시 켜려고.
> - 앱 설치 요청·사용자 동작 없는 업데이트: 이 앱 자신을 자기 GitHub 릴리스에서 업데이트할 때만 씁니다. 설치 전에 받은 파일의 패키지 이름이 같고, 버전 숫자가 더 크고, 서명 인증서가 같은지 확인합니다. 다른 앱은 절대 설치하지 않습니다. 이 기능은 기본으로 꺼져 있고(저장소의 텍스트 파일 스위치), 지금도 꺼져 있습니다.
>
> 검토해 주셔서 감사합니다.

## 확인한 근거 (이 초안의 사실 확인)

- 권한 목록: `app/src/main/AndroidManifest.xml` (2026-11-01). 위 설명과 다른 권한이 추가되면 초안도 고친다.
- 스스로 업데이트 검증(패키지·버전·서명): `app/src/main/java/com/familyshield/app/update/SelfUpdater.kt`. 스위치: `lists/AUTO_UPDATE.txt` (지금 `끄기`).
- 밖으로 나가는 접속 전체: `docs/전체흐름.md` "밖으로 나가는 접속".
- "Harmful app blocked" / "App scan recommended" 의 뜻은 메인 창이 구글 문서(developers.google.com/android/play-protect/warning-dev-guidance, 2026-08-18 갱신본)에서 확인했다고 전함 — 이 창에서는 직접 확인 안 함.
