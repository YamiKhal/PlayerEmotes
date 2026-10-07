package com.yamikhal.playeremotes.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

// client side messages to the local player, API changed in 26.1
public final class Messages {

    private Messages() {}

    public static void show(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        //? if >=26.1 {
        /*player.sendSystemMessage(message);
        *///?} else
        player.displayClientMessage(message, false);
    }

    // short line above the hotbar
    public static void overlay(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else
        player.displayClientMessage(message, true);
    }
}
