// Top-level build file. Plugin versions are declared here via `apply false`,
// version catalog aliases applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
