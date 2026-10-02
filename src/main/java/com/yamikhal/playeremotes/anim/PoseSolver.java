package com.yamikhal.playeremotes.anim;

// turns an evaluated Pose into values for Minecraft's (flat) player model parts. vanilla model parts have no hierarchy,
// so the skeleton hierarchy (arms and head follow the torso) is resolved here with matrices and baked back into each
// part's position and ZYX Euler rotation. channels an animation does not define keep the vanilla value (walk swing,
// held-item poses, head look). the lower limb halves only rotate, relative to their limb (see Bend). not thread
// safe, one instance per render thread
public final class PoseSolver {

    // pivot of the whole-body rotation in entity space (blocks above the feet)
    public static final double BODY_PIVOT_Y = (24 - Part.BODY.pivotY) / 16.0;

    // x, y, z, xRot, yRot, zRot per part (Part#ordinal), vanilla values in, solved values out. lower limb halves
    // only get their rotation, already weighted
    public final float[][] parts = new float[Part.VALUES.length][6];
    // whether the animation bends a limb, per lower limb half (Part#ordinal)
    public final boolean[] bent = new boolean[Part.VALUES.length];
    // whole-body transform for the pose stack: translation in blocks and rotation in radians
    public final double[] bodyTranslation = new double[3];
    public final double[] bodyRotation = new double[3];

    // affine matrices: 3x3 rotation (row major) followed by translation
    private final double[][] modelMatrix = new double[Part.VALUES.length][12];
    private final double[][] vanillaMatrix = new double[Part.VALUES.length][12];
    private final double[] local = new double[12];
    private final double[] tmp = new double[12];
    private final double[] delta = new double[12];
    private final double[] euler = new double[3];

    // look keeps the vanilla head rotation (the player keeps looking around), weight blends between vanilla (0)
    // and the animation (1)
    public void solve(Pose pose, boolean look, float weight) {
        for (int axis = 0; axis < 3; axis++) {
            this.bodyTranslation[axis] = pose.position(Part.BODY, axis) * weight;
            this.bodyRotation[axis] = pose.rotation(Part.BODY, axis) * weight;
        }

        for (Part part : Part.MODEL_PARTS) {
            float[] values = this.parts[part.ordinal()];
            boolean animRot = pose.hasRotation(part) && !(look && part == Part.HEAD);
            boolean animPos = pose.hasPosition(part);

            // transform relative to a resting parent: rotate around the Blockbench pivot for animated rotations,
            // around the model part origin for vanilla ones
            double ex = animRot ? part.pivotX : part.originX;
            double ey = animRot ? part.pivotY : part.originY;
            double ez = animRot ? part.pivotZ : part.originZ;
            double ox = animPos ? pose.position(part, 0) : values[0] - part.originX;
            double oy = animPos ? pose.position(part, 1) : values[1] - part.originY;
            double oz = animPos ? pose.position(part, 2) : values[2] - part.originZ;
            if (animRot) {
                rotation(pose.rotation(part, 0), pose.rotation(part, 1), pose.rotation(part, 2), this.local);
            } else {
                rotation(values[3], values[4], values[5], this.local);
            }

            // local = T(e + offset) * R * T(origin - e)
            double dx = part.originX - ex;
            double dy = part.originY - ey;
            double dz = part.originZ - ez;
            this.local[9] = ex + ox + this.local[0] * dx + this.local[1] * dy + this.local[2] * dz;
            this.local[10] = ey + oy + this.local[3] * dx + this.local[4] * dy + this.local[5] * dz;
            this.local[11] = ez + oz + this.local[6] * dx + this.local[7] * dy + this.local[8] * dz;

            double[] vanilla = this.vanillaMatrix[part.ordinal()];
            rotation(values[3], values[4], values[5], vanilla);
            vanilla[9] = values[0];
            vanilla[10] = values[1];
            vanilla[11] = values[2];

            double[] result = this.modelMatrix[part.ordinal()];
            Part parent = part.parent;
            if (parent == Part.BODY) {
                System.arraycopy(this.local, 0, result, 0, 12);
            } else {
                // children follow how far the emote moved their parent away from its vanilla pose, so vanilla-only
                // torso motion (attacking, crouching) does not drag the arms along
                invertRigid(this.vanillaMatrix[parent.ordinal()], this.tmp);
                multiply(this.modelMatrix[parent.ordinal()], this.tmp, this.delta);
                multiply(this.delta, this.local, result);
            }

            if (part == Part.HEAD && look) {
                // keep looking where the player looks, independent of the torso
                rotation(values[3], values[4], values[5], this.tmp);
                System.arraycopy(this.tmp, 0, result, 0, 9);
            }

            toEulerZYX(result, this.euler);
            values[0] = lerp(weight, values[0], result[9]);
            values[1] = lerp(weight, values[1], result[10]);
            values[2] = lerp(weight, values[2], result[11]);
            values[3] = lerpAngle(weight, values[3], this.euler[0]);
            values[4] = lerpAngle(weight, values[4], this.euler[1]);
            values[5] = lerpAngle(weight, values[5], this.euler[2]);
        }

        // a bend blends in from a straight limb, vanilla never bends one
        for (Part part : Part.LOWER_LIMBS) {
            float[] values = this.parts[part.ordinal()];
            boolean bent = pose.hasRotation(part);
            this.bent[part.ordinal()] = bent;
            for (int axis = 0; axis < 3; axis++) {
                values[3 + axis] = bent ? (float) (pose.rotation(part, axis) * weight) : 0;
            }
        }
    }

    // rotation matrix equal to Rz(z) * Ry(y) * Rx(x), which is how model parts apply their rotation
    private static void rotation(double x, double y, double z, double[] m) {
        double sa = Math.sin(x);
        double ca = Math.cos(x);
        double sb = Math.sin(y);
        double cb = Math.cos(y);
        double sc = Math.sin(z);
        double cc = Math.cos(z);
        m[0] = cc * cb;
        m[1] = cc * sb * sa - sc * ca;
        m[2] = cc * sb * ca + sc * sa;
        m[3] = sc * cb;
        m[4] = sc * sb * sa + cc * ca;
        m[5] = sc * sb * ca - cc * sa;
        m[6] = -sb;
        m[7] = cb * sa;
        m[8] = cb * ca;
    }

    private static void toEulerZYX(double[] m, double[] out) {
        double sy = -m[6];
        if (sy >= 0.99999) {
            out[0] = Math.atan2(m[1], m[4]);
            out[1] = Math.PI / 2;
            out[2] = 0;
        } else if (sy <= -0.99999) {
            out[0] = Math.atan2(-m[1], m[4]);
            out[1] = -Math.PI / 2;
            out[2] = 0;
        } else {
            out[0] = Math.atan2(m[7], m[8]);
            out[1] = Math.asin(sy);
            out[2] = Math.atan2(m[3], m[0]);
        }
    }

    private static void multiply(double[] a, double[] b, double[] out) {
        for (int row = 0; row < 3; row++) {
            int r = row * 3;
            out[r] = a[r] * b[0] + a[r + 1] * b[3] + a[r + 2] * b[6];
            out[r + 1] = a[r] * b[1] + a[r + 1] * b[4] + a[r + 2] * b[7];
            out[r + 2] = a[r] * b[2] + a[r + 1] * b[5] + a[r + 2] * b[8];
            out[9 + row] = a[r] * b[9] + a[r + 1] * b[10] + a[r + 2] * b[11] + a[9 + row];
        }
    }

    private static void invertRigid(double[] m, double[] out) {
        out[0] = m[0];
        out[1] = m[3];
        out[2] = m[6];
        out[3] = m[1];
        out[4] = m[4];
        out[5] = m[7];
        out[6] = m[2];
        out[7] = m[5];
        out[8] = m[8];
        out[9] = -(out[0] * m[9] + out[1] * m[10] + out[2] * m[11]);
        out[10] = -(out[3] * m[9] + out[4] * m[10] + out[5] * m[11]);
        out[11] = -(out[6] * m[9] + out[7] * m[10] + out[8] * m[11]);
    }

    private static float lerp(float t, float from, double to) {
        return (float) (from + (to - from) * t);
    }

    private static float lerpAngle(float t, float from, double to) {
        if (t >= 1) {
            return (float) to;
        }

        double diff = (to - from) % (Math.PI * 2);
        if (diff > Math.PI) {
            diff -= Math.PI * 2;
        }

        if (diff < -Math.PI) {
            diff += Math.PI * 2;
        }

        return (float) (from + diff * t);
    }
}
