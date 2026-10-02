import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Shared helpers for the dev/test launch setup (see scripts/dev.ps1).
 */
object DevRuns {
    /** Port used by the multiplayer test server; override with `-Pmp.port=25570`. */
    fun port(project: Project): Int =
        (project.findProperty("mp.port") as String?)?.toIntOrNull() ?: 25565

    /**
     * Registers `prepareDevServer`, which accepts the EULA and makes the dev server offline-mode
     * (dev clients have no real accounts), and wires it in front of the given server run tasks.
     */
    fun configureServerPreparation(project: Project, runDir: String, serverTasks: List<String>) {
        val prepare = project.tasks.register("prepareDevServer", PrepareDevServerTask::class.java) {
            group = "playeremotes"
            description = "Accepts the EULA and configures an offline-mode dev server"
            directory.set(project.rootProject.layout.projectDirectory.dir(runDir))
            port.set(port(project))
        }
        project.tasks.matching { it.name in serverTasks }.configureEach { dependsOn(prepare) }
    }

    /**
     * Registers `prepareDevClients`, which skips first-launch screens (accessibility onboarding,
     * multiplayer warning, tutorial) in the given game directories. Those screens would otherwise
     * block `--quickPlayMultiplayer` from joining the test server automatically.
     */
    fun configureClientPreparation(project: Project, runDirs: List<String>, clientTasks: List<String>) {
        val prepare = project.tasks.register("prepareDevClients", PrepareDevClientsTask::class.java) {
            group = "playeremotes"
            description = "Skips first-launch screens in the dev client directories"
            directories.from(runDirs.map { project.rootProject.layout.projectDirectory.dir(it) })
        }
        project.tasks.matching { it.name in clientTasks }.configureEach { dependsOn(prepare) }
    }
}

@DisableCachingByDefault(because = "Writes local run files")
abstract class PrepareDevClientsTask : DefaultTask() {
    @get:Internal
    abstract val directories: ConfigurableFileCollection

    @TaskAction
    fun prepare() {
        val forced = linkedMapOf(
            "onboardAccessibility" to "false",
            "skipMultiplayerWarning" to "true",
            "joinedFirstServer" to "true",
            "tutorialStep" to "none",
            "pauseOnLostFocus" to "false",
        )
        for (dir in directories.files) {
            dir.mkdirs()
            val file = dir.resolve("options.txt")
            val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
            for ((key, value) in forced) {
                val index = lines.indexOfFirst { it.startsWith("$key:") }
                if (index < 0) lines += "$key:$value" else lines[index] = "$key:$value"
            }
            file.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }
}

@DisableCachingByDefault(because = "Writes local run files")
abstract class PrepareDevServerTask : DefaultTask() {
    @get:Internal
    abstract val directory: DirectoryProperty

    @get:Input
    abstract val port: Property<Int>

    private companion object {
        /** Settings required for local testing, overwritten even when the user changed them. */
        val FORCED = setOf("online-mode", "server-port", "enforce-secure-profile", "white-list")
    }

    @TaskAction
    fun prepare() {
        val dir = directory.get().asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")

        val file = dir.resolve("server.properties")
        val wanted = linkedMapOf(
            "online-mode" to "false",
            "server-port" to port.get().toString(),
            "spawn-protection" to "0",
            "motd" to "PlayerEmotes dev server",
            "enforce-secure-profile" to "false",
            // 26.3 servers default to an enabled whitelist
            "white-list" to "false",
            "gamemode" to "creative",
            "level-type" to "minecraft\\:flat",
            "generate-structures" to "false",
        )
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
        // Only force the settings required for local testing, keep any other user edits
        for ((key, value) in wanted) {
            val index = lines.indexOfFirst { it.startsWith("$key=") }
            val forced = key in FORCED
            when {
                index < 0 -> lines += "$key=$value"
                forced -> lines[index] = "$key=$value"
            }
        }
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
