# محیط ساخت (Build environment)

## نسخه‌های قفل‌شده و دلیل انتخاب

| ابزار | نسخه | فایل مرجع | دلیل / سازگاری |
| --- | --- | --- | --- |
| JDK | 17 (Temurin 17.0.x) | `docker/Dockerfile` | AGP 8.x به JDK 17 نیاز دارد؛ JDK 21 هم کار می‌کند ولی برای ثبات روی 17 قفل شده |
| Gradle | 8.9 | `gradle/wrapper/gradle-wrapper.properties` | AGP 8.7.3 حداقل Gradle 8.9 می‌خواهد |
| Android Gradle Plugin | 8.7.3 | `gradle/libs.versions.toml` | compileSdk 35 نیازمند AGP ≥ 8.6 است |
| Kotlin | 2.0.21 | `gradle/libs.versions.toml` | با پلاگین `org.jetbrains.kotlin.plugin.compose` هم‌نسخه |
| compileSdk / targetSdk | 35 | `app/build.gradle.kts` | خواسته پروژه |
| minSdk | 26 | `app/build.gradle.kts` | نیازمند VpnService پایدار + `java.time` |
| Build Tools | 35.0.0 | `docker/Dockerfile` | هم‌نسخه با compileSdk 35 |
| NDK | 27.0.12077973 | `docker/Dockerfile` | برای build فعلی لازم نیست (کتابخانه WireGuard به‌صورت AAR با `.so` آماده می‌آید) |
| CMake | 3.22.1 | `docker/Dockerfile` | فقط برای buildهای native آینده |
| Ninja | نسخه سیستم (≥1.10) | `docker/Dockerfile` | AGP/CMake از PATH استفاده می‌کنند |
| Compose BOM | 2024.10.00 | `gradle/libs.versions.toml` | material3 1.3.x |
| WireGuard tunnel | 1.0.20231018 | `gradle/libs.versions.toml` | AAR رسمی از Maven Central، compileSdk 34، Java 17، `.so` آماده |
| Android SDK CLI Tools | 11076708 (با SHA-256 قفل‌شده) | `docker/Dockerfile` | منبع رسمی Google |

## مسیر ۱: Docker (تکرارپذیر، پیشنهادی)

```bash
make docker-image          # = docker build -t vz-booster-build:jdk17-sdk35 docker/
make docker-apk            # APK در dist/ و app/build/outputs/apk/debug/
```

## مسیر ۲: ماشین محلی

```bash
make env-check             # چه چیزی هست، چه چیزی نیست (خروجی با کد ۱ اگر ابزار لازم نباشد)
scripts/bootstrap-tools.sh # نصب دقیق پکیج‌های SDK با نسخه‌های قفل‌شده
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
make apk
```

## مسیر ۳: GitHub Actions (بدون هیچ نصبی)

`.github/workflows/android-ci.yml` روی هر push:
1. JDK 17 Temurin
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:assembleDebug`
4. `aapt2 dump badging` + بررسی محتوای APK (وجود `classes.dex` و `lib/<abi>/libwg-go.so`)
5. آپلود Artifact با نام `vz-network-booster-debug-apk`

در صورت شکست، ابتدای لاگ در `ci/last-build.log` روی همان برنچ کامیت می‌شود (`[skip ci]`).

## عیب‌یابی

| خطا | علت | راه‌حل |
| --- | --- | --- |
| `SDK location not found` | `ANDROID_SDK_ROOT` تنظیم نشده | `export ANDROID_SDK_ROOT=...` یا `local.properties` با `sdk.dir=` |
| `Unsupported class file major version` | JDK نادرست | JDK 17 را فعال کنید (`JAVA_HOME`) |
| `Failed to find Build Tools revision` | build-tools نصب نیست | `sdkmanager "build-tools;35.0.0"` |
| `Could not resolve com.wireguard.android:tunnel` | دسترسی به Maven Central | پروکسی/آینه مخزن را تنظیم کنید |
| `Minimum supported Gradle version is 8.9` | wrapper قدیمی | `gradle/wrapper/gradle-wrapper.properties` را تغییر ندهید |
