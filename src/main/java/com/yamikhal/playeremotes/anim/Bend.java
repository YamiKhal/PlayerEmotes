package com.yamikhal.playeremotes.anim;

// bends a limb at its joint (elbow, knee) by moving the vertices of its cubes, limb stays one closed mesh at any
// angle. model part space (pixels, y down, limb points to +y): vertices above the joint stay, below turn with the
// lower half, the joint ring takes half the turn stretched to meet both halves (mitre joint). split draws two
// rigid halves instead, each closed at the joint. one per limb model part, shared with its outer layer, reused
// every frame
public final class Bend {

    // ring stretch 1 / cos(angle / 2) grows without bound as the limb folds, capped near 132 degrees
    private static final float MAX_STRETCH = 2.5F;

    // false while straight, renders like vanilla then
    public boolean active;
    // two rigid halves instead of one bent limb
    public boolean split;
    // joint y below the part's pivot
    public float jointY;
    // lower half rotation and joint ring map around the joint, row major 3x3
    public final float[] lower = new float[9];
    public final float[] ring = new float[9];
    // lower half rotation as quaternion, for held items
    public float qx;
    public float qy;
    public float qz;
    public float qw = 1;

    private final float[] half = new float[9];

    // rotation in radians like a model part (z, then y, then x)
    public void set(float jointY, float xRot, float yRot, float zRot, boolean split) {
        this.active = true;
        this.split = split;
        this.jointY = jointY;
        float cx = (float) Math.cos(xRot * 0.5);
        float sx = (float) Math.sin(xRot * 0.5);
        float cy = (float) Math.cos(yRot * 0.5);
        float sy = (float) Math.sin(yRot * 0.5);
        float cz = (float) Math.cos(zRot * 0.5);
        float sz = (float) Math.sin(zRot * 0.5);
        float x = cz * cy * sx - sz * sy * cx;
        float y = cz * sy * cx + sz * cy * sx;
        float z = sz * cy * cx - cz * sy * sx;
        float w = cz * cy * cx + sz * sy * sx;
        // shorter way round, so the half turn is too
        if (w < 0) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }

        this.qx = x;
        this.qy = y;
        this.qz = z;
        this.qw = w;
        matrix(x, y, z, w, this.lower);
        // half turn: normalized sum with identity, w >= 0 keeps it away from zero
        float length = (float) Math.sqrt(x * x + y * y + z * z + (w + 1) * (w + 1));
        matrix(x / length, y / length, z / length, (w + 1) / length, this.half);

        // lower half axis b (turned +y). ring stretches along the bend direction, horizontal part of b turned half way,
        // by 1 / cos(angle / 2) = sqrt(2 / (1 + cos(angle)))
        float bx = this.lower[1];
        float by = this.lower[4];
        float bz = this.lower[7];
        float sin = (float) Math.sqrt(bx * bx + bz * bz);
        float stretch = sin < 1.0E-4F ? 1 : Math.min(MAX_STRETCH, (float) Math.sqrt(2 / Math.max(1.0E-6F, 1 + by)));
        if (stretch <= 1) {
            System.arraycopy(this.half, 0, this.ring, 0, 9);
            return;
        }

        float[] h = this.half;
        float ux = (h[0] * bx + h[2] * bz) / sin;
        float uy = (h[3] * bx + h[5] * bz) / sin;
        float uz = (h[6] * bx + h[8] * bz) / sin;
        float k = stretch - 1;
        // ring = (I + k * u * u^T) * half
        for (int column = 0; column < 3; column++) {
            float dot = ux * h[column] + uy * h[3 + column] + uz * h[6 + column];
            this.ring[column] = h[column] + k * ux * dot;
            this.ring[3 + column] = h[3 + column] + k * uy * dot;
            this.ring[6 + column] = h[6 + column] + k * uz * dot;
        }
    }

    private static void matrix(float x, float y, float z, float w, float[] m) {
        m[0] = 1 - 2 * (y * y + z * z);
        m[1] = 2 * (x * y - z * w);
        m[2] = 2 * (x * z + y * w);
        m[3] = 2 * (x * y + z * w);
        m[4] = 1 - 2 * (x * x + z * z);
        m[5] = 2 * (y * z - x * w);
        m[6] = 2 * (x * z - y * w);
        m[7] = 2 * (y * z + x * w);
        m[8] = 1 - 2 * (x * x + y * y);
    }
}
