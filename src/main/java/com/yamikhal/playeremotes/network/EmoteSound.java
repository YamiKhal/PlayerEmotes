package com.yamikhal.playeremotes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

// played at the emoting player when an emote starts, id is a sound event registered through a sounds.json
// (vanilla ids work too), range is in blocks and fades out linearly
public record EmoteSound(ResourceLocation id, float volume, float pitch, float range) {

    public static final float DEFAULT_RANGE = 8;
    public static final float MAX_RANGE = 16;
    private static final int MAX_ID_LENGTH = 256;

    public EmoteSound {
        volume = Math.max(0, Math.min(1, volume));
        pitch = Math.max(0.5F, Math.min(2, pitch));
        range = Math.max(1, Math.min(MAX_RANGE, range));
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
