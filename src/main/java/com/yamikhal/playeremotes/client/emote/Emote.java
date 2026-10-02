package com.yamikhal.playeremotes.client.emote;

import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmoteProp;
import com.yamikhal.playeremotes.network.EmoteSound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// one animation is picked from animations each time the emote is played, variants are named animation
// groups a selector picks from the world state (e.g. left/right), previewSeconds is the pose the menus show
// while the emote is not previewed (-1 for the middle), a partner spec makes it a two-player emote
public record Emote(ResourceLocation id, String pack, Component name, Component description, String author,
                    double previewSeconds, List<ResourceLocation> animations,
                    Map<String, List<ResourceLocation>> variants, @Nullable String selector, Pick pick,
                    boolean look, int blendInTicks, int blendOutTicks, @Nullable EmoteSound sound,
                    @Nullable EmoteProp prop, @Nullable EmoteNetwork.PartnerSpec partner, int order) {

    public enum Pick {
        CYCLE,
        RANDOM
    }

    public String searchText() {
        return (this.name.getString() + " " + this.description.getString() + " " + this.author + " " + this.id).toLowerCase(Locale.ROOT);
    }
}
