package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.network.EmoteSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

// follows the emoting player. vanilla attenuation reaches 16 blocks, so volume fades by hand over the emote's
// range instead. stops with the emote, or when the listener mutes other players' emote sounds
public final class EmoteSoundInstance extends AbstractTickableSoundInstance {

    private final Entity entity;
    private final EmotePlayback playback;
    private final float baseVolume;
    private final float range;
    private final boolean own;

    private EmoteSoundInstance(Entity entity, EmotePlayback playback, EmoteSound sound, boolean own) {
        super(SoundEvent.createVariableRangeEvent(sound.id()), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.playback = playback;
        this.baseVolume = sound.volume();
        this.range = sound.range();
        this.own = own;
        this.pitch = sound.pitch();
        this.attenuation = Attenuation.NONE;
        this.update();
    }

    // sound ends with the playback, also when another emote replaces it
    public static void play(Entity entity, EmotePlayback playback, EmoteSound sound, boolean own) {
        Minecraft.getInstance().getSoundManager().play(new EmoteSoundInstance(entity, playback, sound, own));
    }

    @Override
    public void tick() {
        if (this.entity.isRemoved() || this.playback.isStopping() || EmotePlayers.get(this.entity.getUUID()) != this.playback
                || (!this.own && !PlayerEmotesClient.config().hearOtherSounds)) {
            this.stop();
            return;
        }

        this.update();
    }

    @Override
    public boolean canStartSilent() {
        // players walking into range start hearing it
        return true;
    }

    private void update() {
        this.x = this.entity.getX();
        this.y = this.entity.getEyeY();
        this.z = this.entity.getZ();
        Entity listener = Minecraft.getInstance().getCameraEntity();
        float distance = listener == null || listener == this.entity ? 0 : listener.distanceTo(this.entity);
        this.volume = this.baseVolume * Math.max(0, 1 - distance / this.range);
    }
}
