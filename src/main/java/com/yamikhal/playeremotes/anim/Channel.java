package com.yamikhal.playeremotes.anim;

import java.util.List;

// keyframes of one bone property (rotation or position), Blockbench rules:
// - before the first / after the last keyframe the value is held
// - a segment leaves A with A's post value and arrives at B's pre value
// - A's lerp_mode picks the segment: step holds, catmullrom smooths (also when B is catmullrom), else linear
// - easing/easingArgs (GeckoLib plugin) comes from the destination keyframe B
public final class Channel {

    public enum Lerp {
        LINEAR,
        CATMULLROM,
        STEP
    }

    public record Keyframe(double time, Molang.Expr[] pre, Molang.Expr[] post, Lerp lerp, Ease easing, double easingArg) {}

    private final Keyframe[] frames;

    public Channel(List<Keyframe> frames) {
        this.frames = frames.stream()
                .sorted((a, b) -> Double.compare(a.time, b.time))
                .toArray(Keyframe[]::new);
    }

    public boolean isEmpty() {
        return this.frames.length == 0;
    }

    // for animations without explicit length
    public double lastTime() {
        return this.frames.length == 0 ? 0 : this.frames[this.frames.length - 1].time;
    }

    // writes the three components at time into out
    public void sample(double time, Molang.Context context, double[] out) {
        Keyframe first = this.frames[0];
        if (this.frames.length == 1 || time <= first.time) {
            eval(time <= first.time ? first.pre : first.post, context, out);
            return;
        }

        Keyframe last = this.frames[this.frames.length - 1];
        if (time >= last.time) {
            eval(last.post, context, out);
            return;
        }

        // last keyframe at or before time, baked animations have hundreds so no linear scan
        int i = 0;
        int high = this.frames.length - 2;
        while (i < high) {
            int middle = (i + high + 1) >>> 1;
            if (this.frames[middle].time <= time) {
                i = middle;
            } else {
                high = middle - 1;
            }
        }

        Keyframe a = this.frames[i];
        Keyframe b = this.frames[i + 1];

        if (a.lerp == Lerp.STEP) {
            eval(a.post, context, out);
            return;
        }

        double progress = (time - a.time) / (b.time - a.time);
        if (b.easing != null) {
            progress = b.easing.apply(progress, b.easingArg);
        }

        if (a.lerp == Lerp.CATMULLROM || b.lerp == Lerp.CATMULLROM) {
            Molang.Expr[] p0 = i > 0 ? this.frames[i - 1].post : a.post;
            Molang.Expr[] p3 = i + 2 < this.frames.length ? this.frames[i + 2].pre : b.pre;
            for (int axis = 0; axis < 3; axis++) {
                out[axis] = catmullRom(progress,
                        p0[axis].eval(context), a.post[axis].eval(context),
                        b.pre[axis].eval(context), p3[axis].eval(context));
            }
        } else {
            for (int axis = 0; axis < 3; axis++) {
                double from = a.post[axis].eval(context);
                out[axis] = from + (b.pre[axis].eval(context) - from) * progress;
            }
        }
    }

    private static void eval(Molang.Expr[] vector, Molang.Context context, double[] out) {
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = vector[axis].eval(context);
        }
    }

    private static double catmullRom(double t, double p0, double p1, double p2, double p3) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
