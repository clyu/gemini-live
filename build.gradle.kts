buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 ships built-in Kotlin on KGP 2.2.10; bump KGP so it matches the Compose compiler plugin.
        // Keep this version in sync with `kotlin` in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
