package com.yamikhal.playeremotes.client.emote;

import net.minecraft.network.chat.Component;

import java.util.List;

// sub folder of assets/<namespace>/playeremotes/ with optional pack.json, same named folders in different
// namespaces form one pack, files right in playeremotes/ go to EmoteRegistry#DEFAULT_PACK
public record EmotePack(String id, Component name, Component description, String author, String version,
                        List<Emote> emotes) {}
