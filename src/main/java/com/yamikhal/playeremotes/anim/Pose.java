package com.yamikhal.playeremotes.anim;

import java.util.Arrays;

// one evaluated animation frame, already in Minecraft space:
// - model parts: position in pixels (x, -y, z), rotation in radians (x, y, z)
// - BODY: position in blocks (-x/16, y/16, z/16), rotation in radians (-x, -y, z), applied to the pose stack
// also the Molang context while the frame is evaluated
public final class Pose implements Molang.Context {

    private final double[][] rotation = new double[Part.VALUES.length][3];
    private final double[][] position = new double[Part.VALUES.length][3];
    private final boolean[] hasRotation = new boolean[Part.VALUES.length];
    private final boolean[] hasPosition = new boolean[Part.VALUES.length];
    private final double[] scratch = new double[3];
    private double animTime;
    private double lifeTime;
    // answers player queries while sampling, null gives 0
    private Query.Source queries;

    void reset() {
        Arrays.fill(this.hasRotation, false);
        Arrays.fill(this.hasPosition, false);
    }

    void setTimes(double animTime, double lifeTime) {
        this.animTime = animTime;
        this.lifeTime = lifeTime;
    }

    // who query.* keyframes ask, set before sampling (see EmotePlayback.Frame#sample)
    public void setQueries(Query.Source queries) {
        this.queries = queries;
    }

    double[] scratch() {
        return this.scratch;
    }

    // degrees is the Blockbench rotation
    void setRotation(Part part, double[] degrees) {
        double[] r = this.rotation[part.ordinal()];
        double x = Math.toRadians(finite(degrees[0]));
        double y = Math.toRadians(finite(degrees[1]));
        double z = Math.toRadians(finite(degrees[2]));
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
        double x = finite(pixels[0]);
        double y = finite(pixels[1]);
        double z = finite(pixels[2]);
        if (part == Part.BODY) {
            p[0] = -x / 16;
            p[1] = y / 16;
            p[2] = z / 16;
        } else {
            p[0] = x;
            p[1] = -y;
            p[2] = z;
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

    @Override
    public double query(Query query) {
        return this.queries != null ? this.queries.query(query) : 0;
    }

    // Molang like math.sqrt(-1) gives NaN, that makes the whole player vanish
    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0;
    }
}
