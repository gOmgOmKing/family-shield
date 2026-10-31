#!/usr/bin/env bash
# 앱 서명 열쇠를 처음 한 번만 만든다. 이미 있으면 아무것도 하지 않는다 (덮어쓰면 부모님 폰이 업데이트를 못 받게 됨).
# 만든 뒤 keystore/ 폴더와 keystore.properties 를 반드시 USB 등 두 곳 이상에 백업할 것. GitHub 에는 절대 올리지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -e keystore.properties ] || [ -e keystore/family-shield.jks ]; then
  echo "이미 서명 열쇠가 있습니다. 새로 만들지 않습니다."
  exit 0
fi

JDK="${JAVA_HOME:-$(ls -d "/c/Program Files/Eclipse Adoptium/jdk-17"* 2>/dev/null | tail -1)}"
KEYTOOL="$JDK/bin/keytool"
[ -x "$KEYTOOL" ] || [ -x "$KEYTOOL.exe" ] || { echo "keytool 을 찾지 못했습니다 (JDK 17 필요)."; exit 1; }

mkdir -p keystore
# 비밀번호는 무작위로 만들어 keystore.properties 에만 적는다 (화면·로그에 내지 않음)
SHIELD_KS_PASS="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-32)"
export SHIELD_KS_PASS

if ! out="$("$KEYTOOL" -genkeypair -noprompt \
  -keystore keystore/family-shield.jks -storetype PKCS12 \
  -alias family-shield -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Family Shield" \
  -storepass:env SHIELD_KS_PASS -keypass:env SHIELD_KS_PASS 2>&1)"; then
  echo "서명 열쇠 만들기 실패:"
  echo "$out"
  exit 1
fi

umask 077
cat > keystore.properties <<PROPS
# 앱 서명 열쇠 정보 — GitHub 에 올리지 말 것 (.gitignore 에 들어 있음). 반드시 백업.
storeFile=keystore/family-shield.jks
storePassword=$SHIELD_KS_PASS
keyAlias=family-shield
keyPassword=$SHIELD_KS_PASS
PROPS
unset SHIELD_KS_PASS

echo "서명 열쇠를 만들었습니다: keystore/family-shield.jks + keystore.properties"
echo "지금 바로 두 파일을 USB 등 두 곳 이상에 백업하세요. 잃어버리면 부모님 폰에 앱을 지우고 새로 깔아야 합니다."
