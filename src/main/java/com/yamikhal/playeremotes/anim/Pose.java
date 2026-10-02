package com.yamikhal.playeremotes.anim;

import java.util.Arrays;

// one evaluated frame of an animation, already converted from Blockbench to Minecraft space:
// - model parts: position in pixels (x, -y, z), rotation in radians (x, y, z)
// - BODY: position in blocks (-x/16, y/16, z/16), rotation in radians (-x, -y, z), applied to the pose stack in entity space
// also acts as the Molang context while the frame is evaluated
public final class Pose implements Molang.Context {

    private final double[][] rotation = new double[Part.VALUES.length][3];
    private final double[][] position = new double[Part.VALUES.length][3];
    private final boolean[] hasRotation = new boolean[Part.VALUES.length];
    private final boolean[] hasPosition = new boolean[Part.VALUES.length];
    private final double[] scratch = new double[3];
    private double animTime;
    private double lifeTime;

    void reset() {
        Arrays.fill(this.hasRotation, false);
        Arrays.fill(this.hasPosition, false);
    }

    void setTimes(double animTime, double lifeTime) {
        this.animTime = animTime;
        this.lifeTime = lifeTime;
    }

    double[] scratch() {
        return this.scratch;
    }

    // degrees is the Blockbench rotation
    void setRotation(Part part, double[] degrees) {
        double[] r = this.rotation[part.ordinal()];
        double x = Math.toRadians(degrees[0]);
        double y = Math.toRadians(degrees[1]);
        double z = Math.toRadians(degrees[2]);
        if (part == Part.BODY) {
            r[0] = -x;
            r[1] = -y;
        } else {
            r[0] = x;
            r[1] = y;
        }

        r[2] = z;
        this.hasRotation[part.ordinal()] = true;
    }

    // pixels is the Blockbench position offset
    void setPosition(Part part, double[] pixels) {
        double[] p = this.position[part.ordinal()];
        if (part == Part.BODY) {
            p[0] = -pixels[0] / 16;
            p[1] = pixels[1] / 16;
            p[2] = pixels[2] / 16;
        } else {
            p[0] = pixels[0];
            p[1] = -pixels[1];
            p[2] = pixels[2];
        }

        this.hasPosition[part.ordinal()] = true;
    }

    public boolean hasRotation(Part part) {
        return this.hasRotation[part.ordinal()];
    }

    public boolean hasPosition(Part part) {
        return this.hasPosition[part.ordinal()];
    }

    public double rotation(Part part, int axis) {
        return this.hasRotation[part.ordinal()] ? this.rotation[part.ordinal()][axis] : 0;
    }

    public double position(Part part, int axis) {
        return this.hasPosition[part.ordinal()] ? this.position[part.ordinal()][axis] : 0;
    }

    @Override
    public double animTime() {
        return this.animTime;
    }

    @Override
    public double lifeTime() {
        return this.lifeTime;
    }
}
