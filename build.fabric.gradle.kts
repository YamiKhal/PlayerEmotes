plugins {
    // Applies the correct Loom variant for the Minecraft version (remapping before 26.1, plain after)
    id("dev.kikugie.loom-back-compat")
}

val mpPort = DevRuns.port(project)
// DO NOT set group = ...!
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-fabric"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings on obfuscated versions, no-op on 26.1+
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run/client")
    }

    runs {
        named("client") {
            programArguments.addAll("--username", "Dev")
        }
        named("server") {
            runDirectory = rootProject.file("run/server-${sc.current.version}")
            programArguments.addAll("nogui")
        }
        // Multiplayer test setup: dedicated server + two clients joining it automatically
        register("mpServer") {
            server()
            displayName = "MP Server"
            runDirectory = rootProject.file("run/server-${sc.current.version}")
            programArguments.addAll("nogui")
        }
        register("mpClientA") {
            client()
            displayName = "MP Client A"
            runDirectory = rootProject.file("run/client")
            programArguments.addAll("--username", "PlayerA", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
        register("mpClientB") {
            client()
            displayName = "MP Client B"
            runDirectory = rootProject.file("run/client2")
            programArguments.addAll("--username", "PlayerB", "--quickPlayMultiplayer", "localhost:$mpPort")
        }
        // Joins the dev server and plays every emote in third person
        register("showcase") {
            client()
            displayName = "Emote Showcase"
            runDirectory = rootProject.file("run/client")
            jvmArguments.add("-Dplayeremotes.showcase=true")
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
            // Minimum loader players need; development uses deps.fabric_loader
            "loader" to "0.15.0",
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("fabric.mod.json") { expand(props) }

        // Loom adds the refmap entry itself
        val mixinProps = MixinConfig.properties(sc.current.version, requiredJava, refmap = false, fabric = true)
        mixinProps.forEach { (k, v) -> inputs.property("mixin_$k", v) }
        filesMatching("*.mixins.json") { expand(mixinProps) }

        exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "pack.mcmeta")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"
        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}
