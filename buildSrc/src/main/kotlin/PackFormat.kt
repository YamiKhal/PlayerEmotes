/** `pack.mcmeta` format entries, only Forge requires the mod to ship this file. */
object PackFormat {
    fun forVersion(minecraft: String): String = when (minecraft) {
        "1.20.1" -> "\"pack_format\": 15"
        "1.21.1" -> "\"pack_format\": 34"
        "1.21.4" -> "\"pack_format\": 46"
        // 1.21.9 replaced pack_format with a supported range
        "1.21.11" -> "\"min_format\": [94, 1], \"max_format\": 94"
        else -> error("No pack format known for Minecraft $minecraft")
    }
}
