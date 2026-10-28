#!/usr/bin/env bash
# Builds ScanMart.apk without Gradle (framework-only app + ZXing core).
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
FRAMEWORK_RES=/usr/share/android-framework-res/framework-res.apk   # newer attrs (exported etc.)
ZXING=/usr/share/java/core-3.4.1.jar
OUT=build
rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes $OUT/dex

aapt package -f -m -J $OUT/gen -M AndroidManifest.xml -S res -I $FRAMEWORK_RES -F $OUT/res.apk --min-sdk-version 23 --target-sdk-version 34
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR:$ZXING" -d $OUT/classes $(find src $OUT/gen -name '*.java')
dalvik-exchange --dex --min-sdk-version=23 --output=$OUT/dex/classes.dex $OUT/classes $ZXING
cp $OUT/res.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -q ../unsigned.apk classes.dex)
zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
[ -f release.jks ] || keytool -genkeypair -keystore release.jks -storepass scanmart -keypass scanmart \
  -alias scanmart -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=ScanMart, O=ScanMart, C=US"
apksigner sign --ks release.jks --ks-pass pass:scanmart --key-pass pass:scanmart \
  --min-sdk-version 23 --out ScanMart.apk $OUT/aligned.apk
apksigner verify --verbose ScanMart.apk | head -8
ls -la ScanMart.apk
