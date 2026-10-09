import net.minecraftforge.gradle.SlimeLauncherOptions

plugins {
    // ForgeGradle 7, used for Forge 1.21+ (Forge 1.20.1 uses build.legacyforge.gradle.kts)
    id("net.minecraftforge.gradle") version "7.0.40"
    id("me.modmuss50.mod-publish-plugin")
}

val mpPort = DevRuns.port(project)
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-forge"

val requiredJava = JavaVersion.VERSION_21
val modId = property("mod.id") as String
val forgeVersion = property("deps.forge") as String

minecraft {
    mappings("official", sc.current.version)

    runs {
        // ForgeGradle only fills in Forge's own run names (client, server). Extra runs repeat what
        // Forge's userdev config sets up for those, see `runs` in the forge userdev config.json.
        val clientLaunch = listOf("--launchTarget", "forge_userdev_client", "--version", "MOD_DEV",
                "--assetIndex", "{asset_index}", "--assetsDir", "{assets_root}", "--gameDir", ".")
        val serverLaunch = listOf("--launchTarget", "forge_userdev_server", "--gameDir", ".")
        val serverDir = rootProject.file("run/server-${sc.current.version}")

        fun SlimeLauncherOptions.customClient(dir: String, vararg extra: String) {
            mainClass.set("net.minecraftforge.bootstrap.ForgeBootstrap")
            workingDir.set(rootProject.file(dir))
            args(clientLaunch)
            args(*extra)
        }

        configureEach {
            // Forge only reads mixin configs from jar manifests, which dev runs don't have
            args("--mixin.config=$modId.mixins.json")
        }
        register("client") {
            workingDir.set(rootProject.file("run/client"))
            args("--username", "Dev")
        }
        register("server") {
            workingDir.set(serverDir)
            args("--nogui")
        }
        register("mpServer") {
            mainClass.set("net.minecraftforge.bootstrap.ForgeBootstrap")
            workingDir.set(serverDir)
            args(serverLaunch)
            args("--nogui")
        }
        register("mpClientA") {
            customClient("run/client", "--username", "PlayerA", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
        register("mpClientB") {
            customClient("run/client2", "--username", "PlayerB", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
        // Joins the dev server and plays every emote in third person
        register("showcase") {
            customClient("run/client", "--username", "Showcase", "--quickPlayMultiplayer", "localhost:$mpPort")
            systemProperty("playeremotes.showcase", "true")
        }
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$forgeVersion"))
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

DevRuns.configureServerPreparation(project, "run/server-${sc.current.version}", listOf("runServer", "runMpServer"))
DevRuns.configureClientPreparation(project, listOf("run/client", "run/client2"), listOf("runClient", "runMpClientA", "runMpClientB", "runShowcase"))

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf<String, String>(
            "id" to sc.properties["mod.id"],
            "name" to sc.properties["mod.name"],
            "version" to sc.properties["mod.version"],
            "authors" to sc.properties["mod.authors"],
            "description" to sc.properties["mod.description"],
            "license" to sc.properties["mod.license"],
            "minecraft" to sc.properties["mod.mc_compat"],
            "loader" to forgeVersion.substringAfter('-').substringBefore('.'),
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("META-INF/mods.toml") { expand(props) }
        val packFormat = PackFormat.forVersion(sc.current.version)
        inputs.property("pack_format", packFormat)
        filesMatching("pack.mcmeta") { expand("pack_format" to packFormat) }

        // Forge 1.20.6+ runs with Mojang names, so no refmap is needed
        val mixinProps = MixinConfig.properties(sc.current.version, requiredJava, refmap = false)
        mixinProps.forEach { (k, v) -> inputs.property("mixin_$k", v) }
        filesMatching("*.mixins.json") { expand(mixinProps) }

        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    jar {
        // Forge only loads mixin configs listed in the manifest
        manifest.attributes("MixinConfigs" to "$modId.mixins.json")
    }

    named("compileJava") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
        inputs.property("version", project.property("mod.version"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}

// CurseForge + Modrinth uploads, see Publishing.kt
Publishing.configure(project, "forge", tasks.jar.flatMap { it.archiveFile }, requiredJava)
