package com.yamikhal.playeremotes.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yamikhal.playeremotes.anim.Bend;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

// one limb cube, cut at the joint and drawn bent (see Bend). baked once from what the cube draws, so UVs,
// mirroring, inflation and left out faces stay vanilla's: faces crossing the joint get cut there, which adds the
// joint ring. joint sits at the cube's center across, so limb, outer layer and armor bend alike. split bends get a
// cap per half at the joint, copies of the cube's end faces, drawn only then
public final class LimbMesh {

    private static final byte UPPER = 0;
    private static final byte RING = 1;
    private static final byte LOWER = 2;
    private static final float EPSILON = 1.0E-4F;
    // scratch for render, render thread only
    private static final Vector3f POSITION = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private final float jointY;
    private final float jointX;
    private final float jointZ;
    private final int quads;
    // quads from here on are the joint caps
    private final int caps;
    // x, y, z in pixels, u, v per vertex, four vertices per quad
    private final float[] vertices;
    private final byte[] sides;
    // normal per quad, and whether it turns with the lower half
    private final float[] normals;
    private final boolean[] lowerNormals;

    private LimbMesh(float jointX, float jointY, float jointZ, List<float[][]> quads, List<byte[]> sides, List<float[]> normals,
                     List<Boolean> lowerNormals, int caps) {
        this.jointX = jointX;
        this.jointY = jointY;
        this.jointZ = jointZ;
        this.quads = quads.size();
        this.caps = caps;
        this.vertices = new float[this.quads * 20];
        this.sides = new byte[this.quads * 4];
        this.normals = new float[this.quads * 3];
        this.lowerNormals = new boolean[this.quads];
        for (int quad = 0; quad < this.quads; quad++) {
            for (int corner = 0; corner < 4; corner++) {
                System.arraycopy(quads.get(quad)[corner], 0, this.vertices, (quad * 4 + corner) * 5, 5);
                this.sides[quad * 4 + corner] = sides.get(quad)[corner];
            }

            System.arraycopy(normals.get(quad), 0, this.normals, quad * 3, 3);
            this.lowerNormals[quad] = lowerNormals.get(quad);
        }
    }

    // meshes of a model part's cubes, cached ones reused. null if a cube is not vanilla's (another mod draws it its
    // own way), that part stays straight
    @Nullable
    public static LimbMesh[] of(List<ModelPart.Cube> cubes, @Nullable LimbMesh[] cached, float jointY) {
        if (cached != null && cached.length == cubes.size() && (cached.length == 0 || cached[0].jointY == jointY)) {
            return cached;
        }

        for (ModelPart.Cube cube : cubes) {
            if (cube.getClass() != ModelPart.Cube.class) {
                return null;
            }
        }

        LimbMesh[] meshes = new LimbMesh[cubes.size()];
        for (int i = 0; i < meshes.length; i++) {
            meshes[i] = bake(cubes.get(i), jointY);
        }

        return meshes;
    }

    // jointY in the cube's model part space
    private static LimbMesh bake(ModelPart.Cube cube, float jointY) {
        Recorder recorder = new Recorder();
        //? if >=1.21 {
        cube.compile(new PoseStack().last(), recorder, 0, 0, -1);
        //?} else
        /*cube.compile(new PoseStack().last(), recorder, 0, 0, 1, 1, 1, 1);*/

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (float[] vertex : recorder.vertices) {
            minX = Math.min(minX, vertex[0]);
            maxX = Math.max(maxX, vertex[0]);
            minY = Math.min(minY, vertex[1]);
            maxY = Math.max(maxY, vertex[1]);
            minZ = Math.min(minZ, vertex[2]);
            maxZ = Math.max(maxZ, vertex[2]);
        }

        List<float[][]> quads = new ArrayList<>();
        List<byte[]> sides = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<Boolean> lowerNormals = new ArrayList<>();
        List<float[][]> ends = new ArrayList<>(2);
        boolean cut = false;
        int count = recorder.vertices.size() / 4 * 4;
        for (int start = 0; start < count; start += 4) {
            float[][] quad = new float[4][];
            boolean upper = false;
            boolean lower = false;
            for (int corner = 0; corner < 4; corner++) {
                quad[corner] = recorder.vertices.get(start + corner);
                byte side = side(quad[corner][1], jointY);
                upper |= side == UPPER;
                lower |= side == LOWER;
            }

            float[] normal = {quad[0][5], quad[0][6], quad[0][7]};
            if (isFlat(quad, minY) || isFlat(quad, maxY)) {
                ends.add(quad);
            }

            if (!upper || !lower) {
                add(quads, sides, normals, lowerNormals, List.<float[][]>of(quad), jointY, normal, lower);
                continue;
            }

            // walk the outline, every edge crossing the joint gets a ring vertex shared by both halves
            List<float[]> top = new ArrayList<>(5);
            List<float[]> bottom = new ArrayList<>(5);
            for (int corner = 0; corner < 4; corner++) {
                float[] a = quad[corner];
                float[] b = quad[(corner + 1) % 4];
                byte sideA = side(a[1], jointY);
                byte sideB = side(b[1], jointY);
                if (sideA != LOWER) {
                    top.add(a);
                }

                if (sideA != UPPER) {
                    bottom.add(a);
                }

                if (sideA != RING && sideB != RING && sideA != sideB) {
                    float t = (jointY - a[1]) / (b[1] - a[1]);
                    float[] ring = new float[8];
                    for (int i = 0; i < 8; i++) {
                        ring[i] = a[i] + (b[i] - a[i]) * t;
                    }

                    ring[1] = jointY;
                    top.add(ring);
                    bottom.add(ring);
                }
            }

            add(quads, sides, normals, lowerNormals, fan(top), jointY, normal, false);
            add(quads, sides, normals, lowerNormals, fan(bottom), jointY, normal, true);
            cut = true;
        }

        // top end closes the lower half at the joint, bottom end the upper half, both facing out. caps sit a little
        // inside their half, wider cubes further in, so caps of skin, outer layer and armor do not flicker
        int caps = quads.size();
        if (cut) {
            float inset = Math.max(maxX - minX, maxZ - minZ) * 0.025F;
            for (float[][] end : ends) {
                float[][] cap = new float[4][];
                for (int corner = 0; corner < 4; corner++) {
                    cap[corner] = end[corner].clone();
                    cap[corner][1] = isFlat(end, minY) ? jointY + inset : jointY - inset;
                }

                add(quads, sides, normals, lowerNormals, List.<float[][]>of(cap), jointY, new float[]{end[0][5], end[0][6], end[0][7]},
                        isFlat(end, minY));
            }
        }

        return new LimbMesh((minX + maxX) / 2, jointY, (minZ + maxZ) / 2, quads, sides, normals, lowerNormals, caps);
    }

    //? if >=1.21 {
    public void render(PoseStack.Pose pose, VertexConsumer consumer, Bend bend, int light, int overlay, int color) {
    //?} else
    /*public void render(PoseStack.Pose pose, VertexConsumer consumer, Bend bend, int light, int overlay, float red, float green, float blue, float alpha) {*/
        Matrix4f matrix = pose.pose();
        float[] lower = bend.lower;
        boolean split = bend.split;
        for (int quad = 0, count = split ? this.quads : this.caps; quad < count; quad++) {
            float nx = this.normals[quad * 3];
            float ny = this.normals[quad * 3 + 1];
            float nz = this.normals[quad * 3 + 2];
            if (this.lowerNormals[quad]) {
                float x = lower[0] * nx + lower[1] * ny + lower[2] * nz;
                float y = lower[3] * nx + lower[4] * ny + lower[5] * nz;
                nz = lower[6] * nx + lower[7] * ny + lower[8] * nz;
                nx = x;
                ny = y;
            }

            //? if >=1.21 {
            Vector3f normal = pose.transformNormal(nx, ny, nz, NORMAL);
            //?} else
            /*Vector3f normal = pose.normal().transform(nx, ny, nz, NORMAL);*/
            for (int corner = 0; corner < 4; corner++) {
                int vertex = quad * 4 + corner;
                int i = vertex * 5;
                float x = this.vertices[i];
                float y = this.vertices[i + 1];
                float z = this.vertices[i + 2];
                byte side = this.sides[vertex];
                // split: each half turns as one piece, joint vertices included
                float[] m = split ? (this.lowerNormals[quad] ? lower : null) : side == UPPER ? null : side == LOWER ? lower : bend.ring;
                if (m != null) {
                    float dx = x - this.jointX;
                    float dy = y - this.jointY;
                    float dz = z - this.jointZ;
                    x = this.jointX + m[0] * dx + m[1] * dy + m[2] * dz;
                    y = this.jointY + m[3] * dx + m[4] * dy + m[5] * dz;
                    z = this.jointZ + m[6] * dx + m[7] * dy + m[8] * dz;
                }

                Vector3f position = matrix.transformPosition(x / 16, y / 16, z / 16, POSITION);
                //? if >=1.21 {
                consumer.addVertex(position.x(), position.y(), position.z(), color, this.vertices[i + 3], this.vertices[i + 4],
                        overlay, light, normal.x(), normal.y(), normal.z());
                //?} else
                /*consumer.vertex(position.x(), position.y(), position.z(), red, green, blue, alpha, this.vertices[i + 3], this.vertices[i + 4], overlay, light, normal.x(), normal.y(), normal.z());*/
            }
        }
    }

    private static boolean isFlat(float[][] quad, float y) {
        for (float[] vertex : quad) {
            if (Math.abs(vertex[1] - y) > EPSILON) {
                return false;
            }
        }

        return true;
    }

    private static byte side(float y, float jointY) {
        return y < jointY - EPSILON ? UPPER : y > jointY + EPSILON ? LOWER : RING;
    }

    // quads covering a convex outline of three to five vertices (cut quad has four, slanted one may not)
    private static List<float[][]> fan(List<float[]> outline) {
        List<float[][]> quads = new ArrayList<>(2);
        if (outline.size() >= 3) {
            float[] last = outline.get(Math.min(3, outline.size() - 1));
            quads.add(new float[][]{outline.get(0), outline.get(1), outline.get(2), last});
        }

        if (outline.size() == 5) {
            quads.add(new float[][]{outline.get(0), outline.get(3), outline.get(4), outline.get(4)});
        }

        return quads;
    }

    private static void add(List<float[][]> quads, List<byte[]> sides, List<float[]> normals, List<Boolean> lowerNormals,
                            List<float[][]> add, float jointY, float[] normal, boolean lowerNormal) {
        for (float[][] quad : add) {
            byte[] quadSides = new byte[4];
            for (int corner = 0; corner < 4; corner++) {
                quadSides[corner] = side(quad[corner][1], jointY);
            }

            quads.add(quad);
            sides.add(quadSides);
            normals.add(normal);
            lowerNormals.add(lowerNormal);
        }
    }

    // keeps the vertices a cube draws, in pixels: x, y, z, u, v, normal x, y, z
    private static final class Recorder implements VertexConsumer {

        final List<float[]> vertices = new ArrayList<>(24);

        //? if >=1.21 {
        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.vertices.add(new float[]{x * 16, y * 16, z * 16, u, v, nx, ny, nz});
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
        //?} else {
        /*@Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay,
                           int light, float nx, float ny, float nz) {
            this.vertices.add(new float[]{x * 16, y * 16, z * 16, u, v, nx, ny, nz});
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {}

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unsetDefaultColor() {}
        *///?}

        //? if >=1.21.11 {
        /*@Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
        *///?}

        //? if >=26.3 {
        /*@Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }
        *///?}
    }
}
