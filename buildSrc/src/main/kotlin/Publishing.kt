import me.modmuss50.mpp.ModPublishExtension
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/**
 * CurseForge + Modrinth uploads of one node's jar (mod-publish-plugin).
 *
 * - `gradlew publishMods` uploads every node, `gradlew publishMods -PdryRun` only checks and prints what would go up
 * - tokens from `.env` in the root (`MODRINTH=`, `CURSEFORGE=`), else environment variables of the same name
 * - changelog is the `## <mod.version>` section of CHANGELOG.md, missing section stops the upload
 * - each finished upload leaves a marker in `build/publish/<mod.version>/`, so running it again after a failure only
 *   uploads what is missing instead of duplicating files (delete the folder to upload again on purpose)
 */
object Publishing {
    private const val MODRINTH_ID = "TfUCUpDy"
    private const val CURSEFORGE_ID = "1157644"
    private const val SLUG = "player-emotes"
    private const val TITLE = "Player Emotes"

    fun configure(project: Project, loader: String, jar: Provider<RegularFile>, java: JavaVersion) {
        val modVersion = project.property("mod.version") as String
        val mc = mcReleases(project)
        val loaderName = when (loader) {
            "neoforge" -> "NeoForge"
            else -> loader.replaceFirstChar { it.uppercase() }
        }
        val range = if (mc.size == 1) mc.first() else "${mc.first()}-${mc.last()}"
        val dryRun = project.hasProperty("dryRun")
        val env = env(project)

        project.extensions.configure(ModPublishExtension::class.java) {
            file.set(jar)
            version.set("$modVersion+${mc.last()}-$loader")
            displayName.set("$TITLE $modVersion ($loaderName $range)")
            val changelogFile = project.rootProject.file("CHANGELOG.md")
            changelog.set(project.provider { changelog(changelogFile, modVersion) })
            type.set(STABLE)
            modLoaders.add(loader)
            this.dryRun.set(dryRun)

            curseforge {
                projectId.set(CURSEFORGE_ID)
                projectSlug.set(SLUG)
                accessToken.set(project.provider { env["CURSEFORGE"] })
                minecraftVersions.addAll(mc)
                javaVersions.add(java)
                client.set(true)
                server.set(true)
                changelogType.set("markdown")
                if (loader == "fabric") {
                    requires { slug.set("fabric-api") }
                }
            }

            modrinth {
                projectId.set(MODRINTH_ID)
                accessToken.set(project.provider { env["MODRINTH"] })
                minecraftVersions.addAll(mc)
                if (loader == "fabric") {
                    requires { slug.set("fabric-api") }
                }
            }
        }

        // skip uploads that already went through, see above
        val markers = project.rootProject.layout.buildDirectory.dir("publish/$modVersion")
        for (platform in listOf("Curseforge", "Modrinth")) {
            project.tasks.named("publish$platform") {
                val marker = markers.map { it.file("${project.name}-${platform.lowercase()}") }
                onlyIf("not uploaded yet") { dryRun || !marker.get().asFile.exists() }
                doLast {
                    if (!dryRun) {
                        val file = marker.get().asFile
                        file.parentFile.mkdirs()
                        file.writeText("uploaded\n")
                    }
                }
            }
        }
    }

    // mod.mc_releases of the node, stonecutter flattens the list into mod.mc_releases.0, .1, ...
    private fun mcReleases(project: Project): List<String> {
        val releases = generateSequence(0) { it + 1 }
            .map { project.findProperty("mod.mc_releases.$it") as String? }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
        if (releases.isEmpty()) {
            throw GradleException("No mod.mc_releases for ${project.name} in stonecutter.properties.toml")
        }

        return releases
    }

    private fun changelog(file: java.io.File, version: String): String {
        if (!file.exists()) {
            throw GradleException("CHANGELOG.md is missing")
        }

        val lines = file.readLines()
        val start = lines.indexOfFirst { it.trim() == "## $version" }
        if (start < 0) {
            throw GradleException("CHANGELOG.md has no '## $version' section")
        }

        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        val text = lines.subList(start + 1, end).joinToString("\n").trim()
        if (text.isEmpty()) {
            throw GradleException("CHANGELOG.md section '## $version' is empty")
        }

        return text
    }

    // .env of the root (KEY=value, # comments, optional quotes), environment variables fill the gaps
    private fun env(project: Project): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (key in listOf("MODRINTH", "CURSEFORGE")) {
            System.getenv(key)?.let { values[key] = it }
        }

        val file = project.rootProject.file(".env")
        if (file.exists()) {
            for (line in file.readLines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || '=' !in trimmed) continue

                val key = trimmed.substringBefore('=').trim()
                val value = trimmed.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                if (value.isNotEmpty()) {
                    values[key] = value
                }
            }
        }

        return values
    }
}
