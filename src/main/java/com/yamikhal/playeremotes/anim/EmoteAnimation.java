package com.yamikhal.playeremotes.anim;

import java.util.List;
import java.util.Map;

// a parsed Blockbench animation, length is in seconds (the loop period for LOOP), effects are sorted by time
public record EmoteAnimation(String name, double length, LoopMode loop, Map<Part, Bone> bones, List<Effect> effects) {

    public enum LoopMode {
        // plays once, then the emote ends
        ONCE,
        // repeats until cancelled
        LOOP,
        // plays once and holds the last frame until cancelled ("hold_on_last_frame")
        HOLD
    }

    // either channel may be null
    public record Bone(Channel rotation, Channel position) {}

    // sound_effects / particle_effects keyframe in Blockbench, effect is a sound event or particle type id, locator
    // is a bone name (hands for arms, feet for legs), null for the torso
    public record Effect(double time, Kind kind, String effect, String locator, float volume, float pitch) {

        public enum Kind {
            SOUND,
            PARTICLE
        }
    }

    // time within the animation after time seconds of playing
    public double animTime(double time) {
        return switch (this.loop) {
            case LOOP -> this.length > 0 ? time % this.length : 0;
            case ONCE, HOLD -> Math.min(time, this.length);
        };
    }

    public boolean isFinished(double time) {
        return this.loop == LoopMode.ONCE && time >= this.length;
    }

    // time is seconds since the animation started
    public void sample(double time, Pose out) {
        double animTime = this.animTime(time);
        out.reset();
        out.setTimes(animTime, time);

        double[] tmp = out.scratch();
        for (Map.Entry<Part, Bone> entry : this.bones.entrySet()) {
            Part part = entry.getKey();
            Bone bone = entry.getValue();
            if (bone.rotation() != null) {
                bone.rotation().sample(animTime, out, tmp);
                out.setRotation(part, tmp);
            }

            if (bone.position() != null) {
                bone.position().sample(animTime, out, tmp);
                out.setPosition(part, tmp);
            }
        }
    }
}
