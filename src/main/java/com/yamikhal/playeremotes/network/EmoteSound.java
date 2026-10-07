package com.yamikhal.playeremotes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

// played at the emoting player when an emote starts, id is a sound event from a sounds.json (vanilla ids work
// too), range in blocks, fades out linearly
public record EmoteSound(ResourceLocation id, float volume, float pitch, float range) {

    public static final float DEFAULT_RANGE = 8;
    public static final float MAX_RANGE = 16;
    private static final int MAX_ID_LENGTH = 256;

    // NaN gets past Math.min/max and would reach the sound engine and the distance fade
    public EmoteSound {
        volume = Float.isNaN(volume) ? 1 : Math.max(0, Math.min(1, volume));
        pitch = Float.isNaN(pitch) ? 1 : Math.max(0.5F, Math.min(2, pitch));
        range = Float.isNaN(range) ? DEFAULT_RANGE : Math.max(1, Math.min(MAX_RANGE, range));
    }

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.id.toString(), MAX_ID_LENGTH);
        buf.writeFloat(this.volume);
        buf.writeFloat(this.pitch);
        buf.writeFloat(this.range);
    }

    @Nullable
    static EmoteSound read(FriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
        float volume = buf.readFloat();
        float pitch = buf.readFloat();
        float range = buf.readFloat();
        return id == null ? null : new EmoteSound(id, volume, pitch, range);
    }
}
