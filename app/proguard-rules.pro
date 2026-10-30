# WireGuard tunnel backend is loaded reflectively from the AAR; keep its JNI entry points.
-keep class com.wireguard.android.backend.** { *; }
-keep class com.wireguard.config.** { *; }
-keep class com.wireguard.crypto.** { *; }
