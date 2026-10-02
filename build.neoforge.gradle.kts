plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
}

val mpPort = DevRuns.port(project)
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register(property("mod.id") as String) {
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
            // 26.3 deprecates logoFile, and NeoForge shows a warning screen for it on startup
            "icon_key" to if (sc.current.parsed >= "26.3") "iconFile" else "logoFile",
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinProps = MixinConfig.properties(sc.current.version, requiredJava, refmap = false)
        mixinProps.forEach { (k, v) -> inputs.property("mixin_$k", v) }
        filesMatching("*.mixins.json") { expand(mixinProps) }

        exclude("fabric.mod.json", "META-INF/mods.toml", "pack.mcmeta")
    }

    named("createMinecraftArtifacts") {
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
