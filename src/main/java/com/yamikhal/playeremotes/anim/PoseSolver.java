package com.yamikhal.playeremotes.anim;

// turns a Pose into values for the flat vanilla player model parts. vanilla parts have no hierarchy, so the
// skeleton (arms and head follow the torso) is solved here with matrices and baked into each part's position and
// ZYX rotation and scale. channels the animation lacks keep the vanilla value (walk swing, held item poses, head look),
// lower halves only rotate relative to their limb (see Bend). not thread safe, one per render thread
public final class PoseSolver {

    // whole body rotation pivot in entity space (blocks above the feet)
    public static final double BODY_PIVOT_Y = (24 - Part.BODY.pivotY) / 16.0;

    // x, y, z, xRot, yRot, zRot, xScale, yScale, zScale per part (Part#ordinal), vanilla in, solved out. lower halves
    // only get their rotation, already weighted
    public final float[][] parts = new float[Part.VALUES.length][9];
    // whether the animation bends a limb, per lower half (Part#ordinal)
    public final boolean[] bent = new boolean[Part.VALUES.length];
    // whole body transform for the pose stack, translation in blocks, rotation in radians
    public final double[] bodyTranslation = new double[3];
    public final double[] bodyRotation = new double[3];

    // affine matrices: 3x3 rotation (row major) then translation
    private final double[][] modelMatrix = new double[Part.VALUES.length][12];
    private final double[][] vanillaMatrix = new double[Part.VALUES.length][12];
    private final double[] local = new double[12];
    private final double[] tmp = new double[12];
    private final double[] delta = new double[12];
    private final double[] euler = new double[3];
    private final double[] rotation = new double[9];
    private final double[] scale = new double[3];
    // whether another mod scaled the vanilla part, per part
    private final boolean[] vanillaScaled = new boolean[Part.VALUES.length];

    // look keeps the vanilla head rotation (player keeps looking around), weight blends vanilla (0) to animation (1)
    public void solve(Pose pose, boolean look, float weight) {
        for (int axis = 0; axis < 3; axis++) {
            this.bodyTranslation[axis] = pose.position(Part.BODY, axis) * weight;
            this.bodyRotation[axis] = pose.rotation(Part.BODY, axis) * weight;
        }

        for (Part part : Part.MODEL_PARTS) {
            float[] values = this.parts[part.ordinal()];
            boolean animRot = pose.hasRotation(part) && !(look && part == Part.HEAD);
            boolean animPos = pose.hasPosition(part);
            boolean animScale = pose.hasScale(part);

            // transform relative to a resting parent: animated rotation and scale work around the Blockbench pivot,
            // vanilla ones around the model part origin
            double rx = animRot ? part.pivotX : part.originX;
            double ry = animRot ? part.pivotY : part.originY;
            double rz = animRot ? part.pivotZ : part.originZ;
            double sx = animScale ? part.pivotX : part.originX;
            double sy = animScale ? part.pivotY : part.originY;
            double sz = animScale ? part.pivotZ : part.originZ;
            double ox = animPos ? pose.position(part, 0) : values[0] - part.originX;
            double oy = animPos ? pose.position(part, 1) : values[1] - part.originY;
            double oz = animPos ? pose.position(part, 2) : values[2] - part.originZ;
            if (animRot) {
                rotation(pose.rotation(part, 0), pose.rotation(part, 1), pose.rotation(part, 2), this.local);
            } else {
                rotation(values[3], values[4], values[5], this.local);
            }

            // local = T(r + offset) * R * T(s - r) * S * T(origin - s), scale first like Blockbench, then rotation
            double ax = sx - rx;
            double ay = sy - ry;
            double az = sz - rz;
            double bx = part.originX - sx;
            double by = part.originY - sy;
            double bz = part.originZ - sz;
            this.local[9] = rx + ox + this.local[0] * ax + this.local[1] * ay + this.local[2] * az;
            this.local[10] = ry + oy + this.local[3] * ax + this.local[4] * ay + this.local[5] * az;
            this.local[11] = rz + oz + this.local[6] * ax + this.local[7] * ay + this.local[8] * az;
            scaleColumns(this.local, animScale ? pose.scale(part, 0) : values[6], animScale ? pose.scale(part, 1) : values[7],
                    animScale ? pose.scale(part, 2) : values[8]);
            this.local[9] += this.local[0] * bx + this.local[1] * by + this.local[2] * bz;
            this.local[10] += this.local[3] * bx + this.local[4] * by + this.local[5] * bz;
            this.local[11] += this.local[6] * bx + this.local[7] * by + this.local[8] * bz;

            double[] vanilla = this.vanillaMatrix[part.ordinal()];
            rotation(values[3], values[4], values[5], vanilla);
            this.vanillaScaled[part.ordinal()] = values[6] != 1 || values[7] != 1 || values[8] != 1;
            if (this.vanillaScaled[part.ordinal()]) {
                scaleColumns(vanilla, values[6], values[7], values[8]);
            }
            vanilla[9] = values[0];
            vanilla[10] = values[1];
            vanilla[11] = values[2];

            double[] result = this.modelMatrix[part.ordinal()];
            Part parent = part.parent;
            if (parent == Part.BODY) {
                System.arraycopy(this.local, 0, result, 0, 12);
            } else {
                // children follow how far the emote moved their parent from its vanilla pose, so vanilla only torso motion
                // (attacking, crouching) does not drag the arms. a scaled parent scales and moves its children too
                if (this.vanillaScaled[parent.ordinal()]) {
                    invertAffine(this.vanillaMatrix[parent.ordinal()], this.tmp);
                } else {
                    invertRigid(this.vanillaMatrix[parent.ordinal()], this.tmp);
                }

                multiply(this.modelMatrix[parent.ordinal()], this.tmp, this.delta);
                multiply(this.delta, this.local, result);
            }

            // model parts only have rotation and per axis scale, a part under an unevenly scaled and turned parent gets
            // skewed, that is approximated by each axis' length
            decompose(result, this.rotation, this.scale);
            if (part == Part.HEAD && look) {
                // keep looking where the player looks, independent of the torso
                this.euler[0] = values[3];
                this.euler[1] = values[4];
                this.euler[2] = values[5];
            } else {
                toEulerZYX(this.rotation, this.euler);
            }

            values[0] = lerp(weight, values[0], result[9]);
            values[1] = lerp(weight, values[1], result[10]);
            values[2] = lerp(weight, values[2], result[11]);
            values[3] = lerpAngle(weight, values[3], this.euler[0]);
            values[4] = lerpAngle(weight, values[4], this.euler[1]);
            values[5] = lerpAngle(weight, values[5], this.euler[2]);
            values[6] = lerp(weight, values[6], this.scale[0]);
            values[7] = lerp(weight, values[7], this.scale[1]);
            values[8] = lerp(weight, values[8], this.scale[2]);
        }

        // bend blends in from a straight limb, vanilla never bends one
        for (Part part : Part.LOWER_LIMBS) {
            float[] values = this.parts[part.ordinal()];
            boolean bent = pose.hasRotation(part);
            this.bent[part.ordinal()] = bent;
            for (int axis = 0; axis < 3; axis++) {
                values[3 + axis] = bent ? (float) (pose.rotation(part, axis) * weight) : 0;
            }
        }
    }

    // rotation matrix Rz(z) * Ry(y) * Rx(x), same order model parts use
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

    // m * diag(x, y, z), scales the matrix' axes
    private static void scaleColumns(double[] m, double x, double y, double z) {
        for (int row = 0; row < 3; row++) {
            m[row * 3] *= x;
            m[row * 3 + 1] *= y;
            m[row * 3 + 2] *= z;
        }
    }

    // 3x3 into rotation and per axis scale (column lengths), a mirrored matrix flips x so rotation stays a rotation
    private static void decompose(double[] m, double[] rotation, double[] scale) {
        for (int column = 0; column < 3; column++) {
            double length = Math.sqrt(m[column] * m[column] + m[3 + column] * m[3 + column] + m[6 + column] * m[6 + column]);
            // unscaled stays untouched, dividing float noise would tip angles near gimbal lock another way than before
            scale[column] = Math.abs(length - 1) < 1.0E-6 ? 1 : Math.max(length, Pose.MIN_SCALE);
            for (int row = 0; row < 3; row++) {
                rotation[row * 3 + column] = m[row * 3 + column] / scale[column];
            }
        }

        double det = rotation[0] * (rotation[4] * rotation[8] - rotation[5] * rotation[7])
                - rotation[1] * (rotation[3] * rotation[8] - rotation[5] * rotation[6])
                + rotation[2] * (rotation[3] * rotation[7] - rotation[4] * rotation[6]);
        if (det < 0) {
            scale[0] = -scale[0];
            rotation[0] = -rotation[0];
            rotation[3] = -rotation[3];
            rotation[6] = -rotation[6];
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

    // inverse of an affine matrix, scale allowed. parts never reach 0 scale (see Pose#setScale), a broken matrix from
    // another mod's values gives identity
    private static void invertAffine(double[] m, double[] out) {
        double c0 = m[4] * m[8] - m[5] * m[7];
        double c1 = m[5] * m[6] - m[3] * m[8];
        double c2 = m[3] * m[7] - m[4] * m[6];
        double det = m[0] * c0 + m[1] * c1 + m[2] * c2;
        if (Math.abs(det) < 1.0E-12 || !Double.isFinite(det)) {
            out[0] = out[4] = out[8] = 1;
            out[1] = out[2] = out[3] = out[5] = out[6] = out[7] = 0;
            out[9] = out[10] = out[11] = 0;
            return;
        }

        double inv = 1 / det;
        out[0] = c0 * inv;
        out[1] = (m[2] * m[7] - m[1] * m[8]) * inv;
        out[2] = (m[1] * m[5] - m[2] * m[4]) * inv;
        out[3] = c1 * inv;
        out[4] = (m[0] * m[8] - m[2] * m[6]) * inv;
        out[5] = (m[2] * m[3] - m[0] * m[5]) * inv;
        out[6] = c2 * inv;
        out[7] = (m[1] * m[6] - m[0] * m[7]) * inv;
        out[8] = (m[0] * m[4] - m[1] * m[3]) * inv;
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
