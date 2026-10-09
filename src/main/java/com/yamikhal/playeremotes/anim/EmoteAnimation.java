package com.yamikhal.playeremotes.anim;

import java.util.List;
import java.util.Map;

// parsed Blockbench animation, length in seconds (loop period for LOOP), props are bones outside the skeleton by
// lower case name, effects sorted by time
public record EmoteAnimation(String name, double length, LoopMode loop, Map<Part, Bone> bones, Map<String, PropBone> props,
                             List<Effect> effects) {

    public enum LoopMode {
        // plays once, then the emote ends
        ONCE,
        // repeats until cancelled
        LOOP,
        // plays once, holds the last frame until cancelled ("hold_on_last_frame")
        HOLD
    }

    // any channel may be null
    public record Bone(Channel rotation, Channel position, Channel scale) {}

    // bone an AnimatedProp hangs on, any channel may be null
    public record PropBone(Channel rotation, Channel position, Channel scale) {}

    // sound_effects / particle_effects keyframe, effect is a sound event or particle type id, locator a bone name
    // (hands for arms, feet for legs), null for torso
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

    // prop bone at time into out: position in pixels, rotation in degrees (both Blockbench values), scale. false if
    // the animation lacks the bone, out is then the rest pose
    public boolean sampleProp(String bone, double time, Pose context, double[] out) {
        out[0] = out[1] = out[2] = 0;
        out[3] = out[4] = out[5] = 0;
        out[6] = out[7] = out[8] = 1;
        PropBone prop = this.props.get(bone);
        if (prop == null) {
            return false;
        }

        double animTime = this.animTime(time);
        context.setTimes(animTime, time);
        double[] tmp = context.scratch();
        Channel[] channels = {prop.position(), prop.rotation(), prop.scale()};
        for (int i = 0; i < channels.length; i++) {
            if (channels[i] == null) continue;

            channels[i].sample(animTime, context, tmp);
            for (int axis = 0; axis < 3; axis++) {
                // Molang like math.sqrt(-1) gives NaN, keep the rest value
                if (Double.isFinite(tmp[axis])) {
                    out[i * 3 + axis] = tmp[axis];
                }
            }
        }

        return true;
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

            if (bone.scale() != null) {
                bone.scale().sample(animTime, out, tmp);
                out.setScale(part, tmp);
            }
        }
    }
}
