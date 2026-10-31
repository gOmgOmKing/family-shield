#!/usr/bin/env bash
# 새 버전 설치 파일(family-shield.apk)을 만들어 GitHub Releases 에 보관한다. AI 없이 오너가 직접 실행한다.
#   - 윈도우: scripts\release.cmd 더블클릭
#   - 또는 Git Bash 에서: bash scripts/release.sh "바뀐 내용 한 줄"
# 부모님 폰은 자동 업데이트 스위치(lists/AUTO_UPDATE.txt)가 '켜기'일 때만 스스로 받는다. 꺼져 있으면 오너가 직접 설치 — 새버전_설치방법.md
set -euo pipefail
cd "$(dirname "$0")/.."

fail() {
  echo
  echo "❌ $1"
  echo "GitHub 에는 아무것도 올리지 않았습니다."
  exit 1
}

# --- 0. 준비물 확인 (하나라도 없으면 아무것도 바꾸지 않고 멈춤) ---
[ -f keystore.properties ] && [ -f keystore/family-shield.jks ] ||
  fail "서명 열쇠가 없습니다. 백업해 둔 keystore 폴더와 keystore.properties 를 이 폴더에 넣어 주세요."
command -v git >/dev/null || fail "git 이 설치되어 있지 않습니다."
command -v gh >/dev/null || fail "gh(GitHub 프로그램)가 설치되어 있지 않습니다. docs/전체흐름.md '새 버전 내기'를 보세요."
gh auth status >/dev/null 2>&1 || fail "GitHub 로그인이 안 되어 있습니다. 명령창에서 'gh auth login' 을 먼저 해 주세요."
git rev-parse --git-dir >/dev/null 2>&1 || fail "이 폴더가 아직 git 저장소가 아닙니다."
[ -z "$(git status --porcelain)" ] || fail "저장(커밋)하지 않은 변경이 있습니다. 먼저 커밋하거나 되돌려 주세요."

REPO="$(grep '^shield.githubRepo=' gradle.properties | cut -d= -f2 | tr -d '\r')"
[ -n "$REPO" ] && [ "$REPO" != "OWNER/family-shield" ] ||
  fail "gradle.properties 의 shield.githubRepo 를 실제 'GitHub아이디/저장소이름' 으로 바꿔 주세요."

JDK="${JAVA_HOME:-$(ls -d "/c/Program Files/Eclipse Adoptium/jdk-17"* 2>/dev/null | tail -1)}"
[ -d "$JDK" ] || fail "JDK 17 을 찾지 못했습니다."
export JAVA_HOME="$JDK"

SDK="$(grep '^sdk.dir=' local.properties | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\\/g' | tr -d '\r')"
SDK="$(cygpath -u "$SDK" 2>/dev/null || echo "$SDK")"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner.bat"
[ -f "$APKSIGNER" ] || fail "apksigner 를 찾지 못했습니다 (Android SDK build-tools)."

NOTES="${1:-}"
if [ -z "$NOTES" ]; then
  read -r -p "이번에 바뀐 내용을 한 줄로 적어 주세요 (그냥 Enter = '업데이트'): " NOTES || true
fi
NOTES="${NOTES:-업데이트}"

# --- 1. 버전 숫자 1 올리기 (실패하면 원래대로) ---
OLD="$(grep '^versionCode=' version.properties | cut -d= -f2 | tr -d '\r')"
NEW=$((OLD + 1))
TAG="v1.$NEW"
restore() { git checkout -- version.properties 2>/dev/null || true; }
trap 'restore' ERR
sed -i "s/^versionCode=.*/versionCode=$NEW/" version.properties
echo "버전 1.$OLD → 1.$NEW"

# --- 2. 테스트를 통과해야만 서명된 설치 파일을 만든다 ---
./gradlew --no-daemon -q clean :app:testDebugUnitTest :app:assembleRelease || { restore; fail "테스트 또는 빌드 실패. 위 메시지를 확인하세요."; }
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { restore; fail "설치 파일이 만들어지지 않았습니다."; }
"$APKSIGNER" verify "$APK" >/dev/null || { restore; fail "설치 파일 서명 확인 실패."; }

rm -rf dist && mkdir -p dist
cp "$APK" dist/family-shield.apk
printf '%s\n' "$NEW" > dist/version.txt # 자동 업데이트 스위치가 '켜기'인 폰만 이 숫자를 본다

# --- 3. 버전 기록을 GitHub 에 올리고 릴리스 만들기 ---
trap - ERR
git add version.properties
git commit -q -m "릴리스 $TAG"
git tag "$TAG"
git push -q origin HEAD
git push -q origin "$TAG"
gh release create "$TAG" dist/family-shield.apk dist/version.txt --repo "$REPO" --title "광고 방패 1.$NEW" --notes "$NOTES"

echo
echo "✅ 완료: $TAG 를 올렸습니다. 설치 파일: dist/family-shield.apk"
echo "   부모님 폰에 설치하는 방법: 새버전_설치방법.md"
