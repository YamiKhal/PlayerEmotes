import org.gradle.api.JavaVersion

/**
 * Values expanded into `playeremotes.mixins.json`. Mixin classes that only exist for some
 * Minecraft versions are added here instead of commenting them out in JSON.
 */
object MixinConfig {
    fun properties(mc: String, java: JavaVersion, refmap: Boolean): Map<String, String> {
        val client = buildList {
            add("HumanoidModelMixin")
            add("ModelPartMixin")
            add("PlayerModelMixin")
            add("PlayerRendererMixin")
            // Cape and elytra bend with the torso (from 1.21.2 on the cape does that by itself); emote props
            if (atLeast(mc, "1.21.2")) addAll(listOf("PlayerRenderStateMixin", "WingsLayerMixin", "ArmedEntityRenderStateMixin"))
            else addAll(listOf("CapeLayerMixin", "ElytraLayerMixin", "ItemInHandLayerMixin"))
            // Emote previews through the deferred GUI renderer
            if (atLeast(mc, "1.21.9")) addAll(listOf("GameRendererMixin", "GuiGraphicsAccessor"))
        }
        return mapOf(
            "java" to "JAVA_${java.majorVersion}",
            "client_mixins" to client.joinToString(", ") { "\"client.$it\"" },
            "refmap" to if (refmap) "\"refmap\": \"playeremotes.refmap.json\"," else "",
        )
    }

    private fun atLeast(version: String, min: String): Boolean {
        val a = version.split('.').map { it.toIntOrNull() ?: 0 }
        val b = min.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }
}
