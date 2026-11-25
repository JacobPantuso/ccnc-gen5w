#!/bin/bash
# Builds ccnc-home.apk with the plain SDK tools (no Gradle). Output: build/ccnc-home.apk
# Pulls the shared toolkit from ../ccnc-common/java and its fonts + ccNC artwork into assets.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
PLATFORM="$SDK/platforms/$(ls "$SDK/platforms" | sort -V | tail -1)/android.jar"
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
KS_PASS="${KEYSTORE_PASS:-android}"
KS_ALIAS="${KEYSTORE_ALIAS:-androiddebugkey}"
COMMON="$(cd ../ccnc-common && pwd)"

rm -rf build && mkdir -p build/res build/gen build/classes build/assets/fonts
cp -R "$COMMON/fonts/." build/assets/fonts/
cp -R "$COMMON/assets/." build/assets/
[ -d src/main/assets ] && cp -R src/main/assets/. build/assets/
MANIFEST=src/main/AndroidManifest.xml
if [ -n "${PREVIEW:-}" ]; then
  # Off-car preview: stand-in weather classes plus a fake weather service.
  sed 's#</application>#<receiver android:name=".FakeWeatherReceiver" android:exported="true"><intent-filter><action android:name="com.hkmc.intent.action.request_weather_update"/></intent-filter></receiver></application>#' "$MANIFEST" > build/AndroidManifest.xml
  MANIFEST=build/AndroidManifest.xml
fi

"$BT/aapt2" compile --dir src/main/res -o build/res/
"$BT/aapt2" link -o build/unsigned.apk -I "$PLATFORM" \
  --manifest "$MANIFEST" \
  -A build/assets -0 jpg \
  --min-sdk-version 19 --target-sdk-version "${TARGET_SDK:-19}" ${DEBUGGABLE:+--debug-mode} \
  --java build/gen build/res/*.flat

javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
  -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
  -d build/classes $(find src/main/java "$COMMON/java" build/gen ${PREVIEW:+src/preview/java} -name '*.java')

"$BT/d8" --min-api 19 --release --lib "$PLATFORM" --output build $(find build/classes -name '*.class')

(cd build && zip -q -j unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out build/ccnc-home.apk build/aligned.apk
"$BT/apksigner" verify build/ccnc-home.apk
ls -l build/ccnc-home.apk
