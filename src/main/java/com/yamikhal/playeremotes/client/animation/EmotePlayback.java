package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.anim.EmoteAnimation;
import com.yamikhal.playeremotes.network.AnimatedProp;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmoteProp;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// one running emote of one player, times in client ticks (see EmotePlayers#time)
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
    // message that started the partner emote, to record it again (see FlashbackCompat)
    @Nullable
    private EmoteNetwork.PartnerPlay partnerPlay;
    // seconds into the emote its effect keyframes were played up to, NaN before the first tick
    double effectTime = Double.NaN;
    // made on first use (see EmoteProps)
    @Nullable
    ItemStack propStack;
    @Nullable
    ItemStack[] propStacks;

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

    void setLink(PartnerLink link, EmoteNetwork.PartnerPlay play) {
        this.link = link;
        this.partnerPlay = play;
    }

    @Nullable
    public EmoteNetwork.PartnerPlay partnerPlay() {
        return this.partnerPlay;
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

    // starts blending out, removed once the blend is done
    void stop(float now) {
        if (!this.isStopping()) {
            this.stopTime = now;
        }
    }

    // whether blending out is done and the playback can go
    boolean isDone(float now) {
        return this.isStopping() && now - this.stopTime >= this.options.blendOutTicks();
    }

    // stops play once animations at their end
    void update(float now) {
        EmoteAnimation animation = AnimationRegistry.get(this.animationId);
        if (animation == null) {
            this.stop(now);
        } else if (!this.isStopping() && animation.isFinished((now - this.startTime) / 20.0)) {
            this.stop(this.startTime + (float) (animation.length() * 20));
        }
    }

    // null if nothing to show
    @Nullable
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
            // freeze on the frame the emote stopped at while blending out
            elapsed = Math.min(elapsed, this.stopTime - this.startTime);
        }

        if (weight <= 0) {
            return null;
        }

        return new Frame(animation, elapsed / 20.0, weight, this.options.look(), this.options.splitLimbs(), this.link,
                this.options.prop(), this.options.props());
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

    // everything to pose a player for one frame, link is where a partner emote draws the player, prop and props the
    // emote's items (previews draw them from here, players through EmoteProps)
    public record Frame(EmoteAnimation animation, double seconds, float weight, boolean look, boolean splitLimbs,
                        @Nullable PartnerLink link, @Nullable EmoteProp prop, List<AnimatedProp> props) {

        public Frame(EmoteAnimation animation, double seconds, float weight, boolean look, boolean splitLimbs) {
            this(animation, seconds, weight, look, splitLimbs, null, null, List.of());
        }
    }
}
