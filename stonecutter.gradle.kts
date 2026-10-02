plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    properties {
        tags(version, loader)
    }

    // Enables `//? if fabric {`, `//? if forge {`, `//? if neoforge {` in sources
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}

// Dev helpers: `gradlew devTargets` lists every runnable node, see scripts/dev.ps1 for launching.
tasks.register("devTargets") {
    group = "playeremotes"
    description = "Lists all version/loader targets that can be launched"
    val names = stonecutter.versions.map { it.project }
    doLast { names.forEach { println(it) } }
}

// One-click multiplayer launchers per node (Gradle panel: <node> > playeremotes). They run dev.ps1,
// which starts every process in its own window and waits for the server before starting the clients.
val devScript = file("dev.ps1").absolutePath
val devPort = providers.gradleProperty("mp.port").orElse("25565")
val launchers = mapOf(
    "runMp" to ("mp" to "Starts the dev server and two clients (PlayerA, PlayerB) that join it"),
    "runMpClients" to ("clients" to "Starts two clients (PlayerA, PlayerB) that join an already running dev server"),
    "runShowcaseMp" to ("showcase" to "Starts the dev server (if needed) and a client that plays every emote"),
)
for (node in stonecutter.versions) {
    project(":${node.project}") {
        for ((name, launcher) in launchers) {
            tasks.register<Exec>(name) {
                group = "playeremotes"
                description = launcher.second
                commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", devScript,
                    "-Target", node.project, "-Mode", launcher.first, "-Port", devPort.get())
            }
        }
    }
}
