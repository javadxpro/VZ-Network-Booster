# VZ Network Booster

**Game Booster مبتنی بر WireGuard برای اندروید** — مسیر اتصال بازی‌ها را از طریق یک رله WireGuard که
خودتان کنترل می‌کنید عبور می‌دهد و کیفیت مسیر را با اندازه‌گیری واقعی (TCP/UDP) می‌سنجد.

> این پروژه هیچ کاهش پینگ تضمین‌شده‌ای ادعا نمی‌کند. مسیر عوض می‌شود؛ بهتر یا بدتر شدن آن با تست
> واقعی روی گوشی خودتان سنجیده می‌شود و در تب **Measure** نمایش داده می‌شود.

## وضعیت ساخت (Build status)

| مورد | وضعیت |
| --- | --- |
| سورس کامل (Kotlin + Jetpack Compose) | ✅ در همین مخزن |
| تست‌های واحد JVM | ✅ در GitHub Actions اجرا می‌شوند |
| ساخت APK دیباگ | 🟢 در GitHub Actions (Artifact: `vz-network-booster-debug-apk`) |
| محیط Build قابل تکرار | ✅ `docker/Dockerfile` + `scripts/bootstrap-tools.sh` |
| تست واقعی روی گوشی | ⚠️ نیازمند گوشی/شبیه‌ساز و یک رله WireGuard واقعی (در این محیط sandbox ممکن نیست) |

گزارش کامل و صادقانه محدودیت‌ها: [`docs/fa/REPORT.md`](docs/fa/REPORT.md)

## چه چیزی واقعی است و چه چیزی واقعی نیست

واقعی:

- `VpnService` واقعی از طریق بک‌اند رسمی WireGuard (`com.wireguard.android:tunnel`) — تونل با
  کلیدهای واقعی ساخته می‌شود و در صورت عدم موفقیت handshake، خطا نمایش داده می‌شود.
- پروفایل چندبازی (PUBG Mobile Global از ابتدا، + CODM، Delta Force، + افزودن بازی جدید از UI).
- Split Tunneling واقعی: فقط پکیج‌های بازی انتخاب‌شده از تونل عبور می‌کنند (`IncludedApplications`).
- اندازه‌گیری Ping/Jitter/Loss با پروب‌های TCP handshake / UDP round-trip روی همان گوشی.
- Smart Routing قطعی و قابل تست (وزن‌دهی بر اساس تأخیر اندازه‌گیری‌شده + سلامت + متادیتای رله).
- Auto Reconnect با backoff نمایی و Watchdog در Foreground Service.
- ذخیره‌سازی رازها با AES-GCM در Android Keystore؛ هیچ کلیدی در مخزن نیست.

واقعی نیست (و در اپ هم به‌عنوان قابلیت اعلام نشده):

- «سرور عمومی ناشناس امن» — هیچ رله‌ای از پیش داخل اپ نیست؛ رله فقط بعد از ثبت کلید عمومی این
  دستگاه روی سرور کار می‌کند.
- ICMP/ping نصب‌شده به سرور بازی — بازی‌ها به پروب پاسخ نمی‌دهند؛ اپ به‌جای عدد ساختگی،
  «اندازه‌گیری‌نشده» را نشان می‌دهد.
- کاهش تضمین‌شده پینگ.

## شروع سریع

```bash
# 1) بررسی ابزارهای ساخت
make env-check

# 2) نصب خودکار ابزارها (JDK 17 + Android SDK 35 + build-tools 35.0.0 + NDK + CMake)
scripts/bootstrap-tools.sh

# 3) یا محیط کاملاً قابل تکرار با Docker
make docker-image
make docker-apk

# 4) ساخت محلی APK (تست‌های JVM + assembleDebug)
make apk      # خروجی: dist/app-debug.apk
```

راهنمای کامل: [`docs/fa/BUILD.md`](docs/fa/BUILD.md) · نحوه استفاده: [`docs/fa/USAGE.md`](docs/fa/USAGE.md) ·
امنیت: [`docs/fa/SECURITY.md`](docs/fa/SECURITY.md)

## ساختار

```
app/src/main/java/com/vz/networkbooster/
  vpn/        WireGuardConfigBuilder, WgTunnel, VpnManager, BoosterService
  net/        QualityTester, QualityStats, ServerHealthChecker, SmartRouter
  data/       مدل‌ها، مخازن JSON، کاتالوگ بازی‌های داخل اپ، ایمپورت کانفیگ
  security/   SecretBox (Keystore/AES-GCM), DeviceIdentity
  ui/         Compose: Home, Games, Relays, Measure, Settings
docker/       محیط Build قابل تکرار
scripts/      bootstrap-tools.sh, build-apk.sh, verify-toolchain.sh
config/       نمونه bundle رله + راهنمای راه‌اندازی سرور (بدون راز)
```

## ابزارها (نسخه‌های قفل‌شده)

JDK **17** · Gradle **8.9** (wrapper) · AGP **8.7.3** · Kotlin **2.0.21** · compileSdk/targetSdk **35** ·
build-tools **35.0.0** · NDK **27.0.12077973** · CMake **3.22.1** · ninja (سیستم) ·
WireGuard tunnel **1.0.20231018** · Compose BOM **2024.10.00**

## مجوز و مسئولیت

این نرم‌افزار برای استفاده شخصی/آزمایشی است. استفاده از VPN باید با قوانین محلی و شرایط سرویس بازی
سازگار باشد. نویسنده هیچ تضمینی برای کاهش پینگ یا بهبود عملکرد بازی نمی‌دهد.
