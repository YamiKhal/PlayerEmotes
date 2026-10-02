package com.yamikhal.playeremotes.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

// opening and querying screens, which moved from Minecraft to its Gui in 26.3
public final class Screens {

    private Screens() {}

    public static void open(@Nullable Screen screen) {
        //? if >=26.3 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else
        Minecraft.getInstance().setScreen(screen);
    }

    @Nullable
    public static Screen current() {
        //? if >=26.3 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else
        return Minecraft.getInstance().screen;
    }
}
