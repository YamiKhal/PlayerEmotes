plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // CurseForge + Modrinth uploads, see Publishing.kt
    implementation("me.modmuss50:mod-publish-plugin:2.2.1")
}
