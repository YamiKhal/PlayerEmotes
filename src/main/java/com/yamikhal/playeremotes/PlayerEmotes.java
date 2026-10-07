package com.yamikhal.playeremotes;

import com.yamikhal.playeremotes.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerEmotes {

    public static final String MOD_ID = "playeremotes";
    public static final Logger LOGGER = LoggerFactory.getLogger("PlayerEmotes");

    private static Platform platform;

    private PlayerEmotes() {}

    // called by every loader entrypoint, both sides
    public static void init(Platform platform) {
        PlayerEmotes.platform = platform;
    }

    public static Platform platform() {
        return platform;
    }

    public static ResourceLocation id(String path) {
        return id(MOD_ID, path);
    }

    public static ResourceLocation id(String namespace, String path) {
        //? if <1.21 {
        /*return new ResourceLocation(namespace, path);
        *///?} else
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    // parses namespace:path, no namespace means this mod, null if invalid
    @Nullable
    public static ResourceLocation parseId(String value) {
        return value.indexOf(':') < 0 ? ResourceLocation.tryBuild(MOD_ID, value) : ResourceLocation.tryParse(value);
    }
}
