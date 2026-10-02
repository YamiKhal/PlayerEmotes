package com.yamikhal.playeremotes.client.emote;

import net.minecraft.network.chat.Component;

import java.util.List;

// a sub-folder of assets/<namespace>/playeremotes/ described by an optional pack.json, folders with the same name
// in different namespaces form one pack, files directly in playeremotes/ go to EmoteRegistry#DEFAULT_PACK
public record EmotePack(String id, Component name, Component description, String author, String version,
                        List<Emote> emotes) {}
