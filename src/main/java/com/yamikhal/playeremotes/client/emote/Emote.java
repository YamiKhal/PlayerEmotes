package com.yamikhal.playeremotes.client.emote;

import com.yamikhal.playeremotes.network.AnimatedProp;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmoteProp;
import com.yamikhal.playeremotes.network.EmoteSound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// one of animations is picked each play, variants are named animation groups a selector picks from the world
// state (e.g. left/right), previewSeconds is the menu pose while not previewed (-1 for the middle), partner
// makes it a two player emote, splitLimbs draws bent limbs as two rigid halves, props the items its bones move
public record Emote(ResourceLocation id, String pack, Component name, Component description, String author,
                    double previewSeconds, List<ResourceLocation> animations,
                    Map<String, List<ResourceLocation>> variants, @Nullable String selector, Pick pick,
                    boolean look, boolean splitLimbs, int blendInTicks, int blendOutTicks, @Nullable EmoteSound sound,
                    @Nullable EmoteProp prop, List<AnimatedProp> props, @Nullable EmoteNetwork.PartnerSpec partner,
                    int order) {

    public enum Pick {
        CYCLE,
        RANDOM
    }

    public String searchText() {
        return (this.name.getString() + " " + this.description.getString() + " " + this.author + " " + this.id).toLowerCase(Locale.ROOT);
    }
}
