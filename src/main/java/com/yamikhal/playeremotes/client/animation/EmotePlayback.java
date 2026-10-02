package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.anim.EmoteAnimation;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// one running emote of one player, times are in client ticks (see EmotePlayers#time)
public final class EmotePlayback {

    @Nullable
    private final ResourceLocation emoteId;
    private final ResourceLocation animationId;
    private final EmoteNetwork.Options options;
    private final float startTime;
    // when this client learned about the emote
    private final int receivedAt;
    private float stopTime = Float.NaN;
    // set for the two players of a partner emote
    @Nullable
    private PartnerLink link;
    // seconds into the emote up to which its effect keyframes were played, NaN before the first tick
    double effectTime = Double.NaN;
    // created on first use (see EmoteProps)
    @Nullable
    ItemStack propStack;

    EmotePlayback(@Nullable ResourceLocation emoteId, ResourceLocation animationId, EmoteNetwork.Options options,
                  float startTime, int receivedAt) {
        this.emoteId = emoteId;
        this.animationId = animationId;
        this.options = options;
        this.startTime = startTime;
        this.receivedAt = receivedAt;
    }

    @Nullable
    public PartnerLink link() {
        return this.link;
    }

    void setLink(@Nullable PartnerLink link) {
        this.link = link;
    }

    // null if unknown (other players' emotes)
    @Nullable
    public ResourceLocation emoteId() {
        return this.emoteId;
    }

    public ResourceLocation animationId() {
        return this.animationId;
    }

    public EmoteNetwork.Options options() {
        return this.options;
    }

    public float startTime() {
        return this.startTime;
    }

    int receivedAt() {
        return this.receivedAt;
    }

    public boolean isStopping() {
        return !Float.isNaN(this.stopTime);
    }

    // starts blending out, the emote is removed once the blend finished
    void stop(float now) {
        if (!this.isStopping()) {
            this.stopTime = now;
        }
    }

    // whether the playback finished blending out and can be discarded
    boolean isDone(float now) {
        return this.isStopping() && now - this.stopTime >= this.options.blendOutTicks();
    }

    // stops animations that play once when they reach their end
    void update(float now) {
        EmoteAnimation animation = AnimationRegistry.get(this.animationId);
        if (animation == null) {
            this.stop(now);
        } else if (!this.isStopping() && animation.isFinished((now - this.startTime) / 20.0)) {
            this.stop(this.startTime + (float) (animation.length() * 20));
        }
    }

    // null if nothing should be shown
    public Frame frame(float now) {
        EmoteAnimation animation = AnimationRegistry.get(this.animationId);
        if (animation == null) {
            return null;
        }

        float elapsed = now - this.startTime;
        float weight = this.options.blendInTicks() > 0 ? smooth(elapsed / this.options.blendInTicks()) : 1;
        if (this.isStopping()) {
            float out = this.options.blendOutTicks() > 0 ? 1 - (now - this.stopTime) / this.options.blendOutTicks() : 0;
            weight = Math.min(weight, smooth(out));
            // freeze on the frame where the emote was stopped while blending out
            elapsed = Math.min(elapsed, this.stopTime - this.startTime);
        }

        if (weight <= 0) {
            return null;
        }

        return new Frame(animation, elapsed / 20.0, weight, this.options.look(), this.link);
    }

    private static float smooth(float t) {
        if (t <= 0) {
            return 0;
        }

        if (t >= 1) {
            return 1;
        }

        return t * t * (3 - 2 * t);
    }

    // everything needed to pose a player for one frame, link is where a partner emote draws the player
    public record Frame(EmoteAnimation animation, double seconds, float weight, boolean look, @Nullable PartnerLink link) {

        public Frame(EmoteAnimation animation, double seconds, float weight, boolean look) {
            this(animation, seconds, weight, look, null);
        }
    }
}
