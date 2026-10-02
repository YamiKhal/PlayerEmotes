pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the right Fabric Loom variant for obfuscated (<26.1) and unobfuscated (26.1+) versions
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * Creates `versions/{mc}-{loader}` nodes, each built by `build.{loader}.gradle.kts`.
         * Add a Minecraft version here + a matching section in stonecutter.properties.toml.
         */
        fun match(mc: String, vararg loaders: String) {
            for (loader in loaders) {
                // Forge 1.20.1 is built with ModDevGradle, newer Forge with ForgeGradle
                val script = if (loader == "forge" && mc == "1.20.1") "legacyforge" else loader
                version("$mc-$loader", mc).buildscript("build.$script.gradle.kts")
            }
        }

        match("1.20.1", "fabric", "forge")
        match("1.21.1", "fabric", "neoforge", "forge")
        match("1.21.4", "fabric", "neoforge", "forge")
        match("1.21.11", "fabric", "neoforge", "forge")
        match("26.1.2", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")
        vcsVersion = "1.21.1-fabric"
    }
}

rootProject.name = "PlayerEmotes"
