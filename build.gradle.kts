// Top-level build file. AGP 9 ships built-in Kotlin support, so the
// org.jetbrains.kotlin.android plugin is intentionally not applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
