plugins {
    // ModDevGradle's Forge (pre-NeoForge) variant, used for 1.20.1
    id("net.neoforged.moddev.legacyforge") version "2.0.148"
    id("neoforge-mutex")
    id("me.modmuss50.mod-publish-plugin")
}

val mpPort = DevRuns.port(project)
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-forge"

val requiredJava = JavaVersion.VERSION_17
val modId = property("mod.id") as String
val forgeVersion = property("deps.forge") as String

legacyForge {
    version = forgeVersion

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/client")
            programArguments.addAll("--username", "Dev")
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run/server-${sc.current.version}")
            programArgument("nogui")
        }
        register("mpServer") {
            server()
            gameDirectory = rootProject.file("run/server-${sc.current.version}")
            programArgument("nogui")
        }
        register("mpClientA") {
            client()
            gameDirectory = rootProject.file("run/client")
            programArguments.addAll("--username", "PlayerA", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
        register("mpClientB") {
            client()
            gameDirectory = rootProject.file("run/client2")
            programArguments.addAll("--username", "PlayerB", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
        // Joins the dev server and plays every emote in third person
        register("showcase") {
            client()
            gameDirectory = rootProject.file("run/client")
            systemProperty("playeremotes.showcase", "true")
            programArguments.addAll("--username", "Showcase", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
    }
}

mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.mixins.json")
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
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

        val mixinProps = MixinConfig.properties(sc.current.version, requiredJava, refmap = true)
        mixinProps.forEach { (k, v) -> inputs.property("mixin_$k", v) }
        filesMatching("*.mixins.json") { expand(mixinProps) }

        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    jar {
        // Forge only loads mixin configs listed in the manifest
        manifest.attributes("MixinConfigs" to "$modId.mixins.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
        inputs.property("version", project.property("mod.version"))
        from(named<Jar>("reobfJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}

// CurseForge + Modrinth uploads, see Publishing.kt
Publishing.configure(project, "forge", tasks.named<Jar>("reobfJar").flatMap { it.archiveFile }, requiredJava)
