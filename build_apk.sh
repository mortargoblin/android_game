#!/usr/bin/env bash
# Builds the APK without Gradle / the Android Gradle Plugin, using only:
#   kotlinc, aapt2, dx (dalvik-exchange), zipalign, apksigner and an android.jar.
# On Ubuntu/Debian:  apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
# plus a Kotlin compiler (https://github.com/JetBrains/kotlin/releases), either on PATH or via KOTLIN_HOME,
# and ProGuard (https://github.com/Guardsquare/proguard/releases) via PROGUARD_HOME.
#
# Output: build/shipka-1877.apk (debug-signed)
set -euo pipefail
cd "$(dirname "$0")"

APP_ID=com.mortargoblin.shipka
VERSION_CODE=1
VERSION_NAME=1.0
MIN_SDK=21
TARGET_SDK=34

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
KOTLINC=${KOTLIN_HOME:+$KOTLIN_HOME/bin/}kotlinc
# Kotlin >= 2.0 compiles parts of its own stdlib with invokedynamic, which dx can't translate for
# API < 26 (even after shrinking, e.g. AbstractCollection.toString). The 1.9 stdlib is dx-friendly,
# so we pin it (downloaded from Maven Central) and compile against API level 1.9.
KOTLIN_STDLIB_VERSION=1.9.24
KOTLIN_STDLIB=${KOTLIN_STDLIB:-.buildtools/kotlin-stdlib-$KOTLIN_STDLIB_VERSION.jar}
AAPT2=${AAPT2:-aapt2}
DX=${DX:-dalvik-exchange}
PROGUARD_JAR=${PROGUARD_JAR:-${PROGUARD_HOME:?set PROGUARD_HOME or PROGUARD_JAR}/lib/proguard.jar}
KEYSTORE=${KEYSTORE:-debug.keystore}

SRC=app/src/main
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/classes" "$OUT/dex"

if [ ! -f "$KOTLIN_STDLIB" ]; then
    mkdir -p "$(dirname "$KOTLIN_STDLIB")"
    curl -sSfL -o "$KOTLIN_STDLIB" \
        "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_STDLIB_VERSION/kotlin-stdlib-$KOTLIN_STDLIB_VERSION.jar"
fi

echo "==> Compiling resources"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res.zip"

# The source manifest has no package attribute (the Gradle build sets it via `namespace`).
sed "s|<manifest |<manifest package=\"$APP_ID\" |" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

echo "==> Linking resources"
"$AAPT2" link -o "$OUT/unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$OUT/AndroidManifest.xml" \
    --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
    --version-code $VERSION_CODE --version-name $VERSION_NAME \
    "$OUT/res.zip"

echo "==> Compiling Kotlin"
# Lambdas as classes (not invokedynamic) so the classic dx dexer can handle them.
"$KOTLINC" -no-jdk -no-stdlib -no-reflect -jvm-target 1.8 \
    -language-version 1.9 -api-version 1.9 \
    -Xlambdas=class -Xsam-conversions=class -Xstring-concat=inline \
    -cp "$ANDROID_JAR:$KOTLIN_STDLIB" -d "$OUT/classes" \
    $(find "$SRC/java" -name '*.kt')

echo "==> Shrinking"
# Strip unused Kotlin stdlib code. Besides making the APK small, this drops the few stdlib
# methods that use invokedynamic, which the classic dx dexer cannot translate for API < 26.
java -jar "$PROGUARD_JAR" \
    -injars "$OUT/classes" \
    -injars "$KOTLIN_STDLIB(!META-INF/**)" \
    -outjars "$OUT/shrunk.jar" \
    -libraryjars "$ANDROID_JAR" \
    -dontobfuscate -dontoptimize -dontpreverify -ignorewarnings -dontnote \
    -keep "class $APP_ID.** { *; }" > "$OUT/proguard.log"

echo "==> Dexing"
"$DX" --dex --min-sdk-version=$MIN_SDK --output="$OUT/dex/classes.dex" "$OUT/shrunk.jar"

echo "==> Packaging"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/shipka-1877.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/shipka-1877.apk"
echo "==> Built $OUT/shipka-1877.apk ($(du -h "$OUT/shipka-1877.apk" | cut -f1))"
