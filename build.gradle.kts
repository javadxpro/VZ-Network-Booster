// Root build file for VZ Network Booster.
// All plugin versions are pinned in gradle/libs.versions.toml (single source of truth).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
