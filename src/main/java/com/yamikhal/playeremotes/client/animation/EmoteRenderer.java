package com.yamikhal.playeremotes.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yamikhal.playeremotes.anim.Part;
import com.yamikhal.playeremotes.anim.Pose;
import com.yamikhal.playeremotes.anim.PoseSolver;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

// applies emote frames to the player model and pose stack, render thread only
public final class EmoteRenderer {

    // scale the player renderer applies to the model (see PlayerRenderer#scale)
    private static final float PLAYER_MODEL_SCALE = 0.9375F;

    private static final Pose POSE = new Pose();
    private static final PoseSolver SOLVER = new PoseSolver();

    // where effects start on each part, in model units from the part's pivot
    private static final float[][] LOCATOR_ENDS = new float[Part.VALUES.length][];

    // set while the first person hand is rendered, which must not be animated
    public static boolean renderingHand;

    static {
        LOCATOR_ENDS[Part.BODY.ordinal()] = new float[]{0, 6, 0};
        LOCATOR_ENDS[Part.TORSO.ordinal()] = new float[]{0, 6, 0};
        LOCATOR_ENDS[Part.HEAD.ordinal()] = new float[]{0, -4, 0};
        LOCATOR_ENDS[Part.RIGHT_ARM.ordinal()] = new float[]{-1, 10, 0};
        LOCATOR_ENDS[Part.LEFT_ARM.ordinal()] = new float[]{1, 10, 0};
        LOCATOR_ENDS[Part.RIGHT_LEG.ordinal()] = new float[]{0, 12, 0};
        LOCATOR_ENDS[Part.LEFT_LEG.ordinal()] = new float[]{0, 12, 0};
    }

    private EmoteRenderer() {}

    // poses the model parts, call after vanilla posed the model
    public static void poseModel(HumanoidModel<?> model, EmotePlayback.Frame frame) {
        ModelPart[] parts = parts(model);
        for (int i = 0; i < parts.length; i++) {
            ModelPart part = parts[i];
            float[] values = SOLVER.parts[Part.MODEL_PARTS[i].ordinal()];
            values[0] = part.x;
            values[1] = part.y;
            values[2] = part.z;
            values[3] = part.xRot;
            values[4] = part.yRot;
            values[5] = part.zRot;
        }

        frame.animation().sample(frame.seconds(), POSE);
        SOLVER.solve(POSE, frame.look(), frame.weight());

        for (int i = 0; i < parts.length; i++) {
            ModelPart part = parts[i];
            float[] values = SOLVER.parts[Part.MODEL_PARTS[i].ordinal()];
            part.x = values[0];
            part.y = values[1];
            part.z = values[2];
            part.xRot = values[3];
            part.yRot = values[4];
            part.zRot = values[5];
        }
    }

    // draws a player of a partner emote at their spot and facing (see PartnerLink) instead of where they stand, eased
    // in and out with the emote. their height stays, so nobody sinks into a block. x/z is where the renderer draws the
    // player this frame and bodyRot the body facing it applied. call right after the renderer's setupRotations, before
    // poseBody
    public static void alignPartner(PoseStack poseStack, EmotePlayback.Frame frame, double x, double z, float bodyRot) {
        PartnerLink link = frame.link();
        if (link == null) {
            return;
        }

        float weight = frame.weight();
        float yaw = bodyRot + Mth.wrapDegrees(link.yaw() - bodyRot) * weight;
        // undo the renderer's body turn, move in world space, then turn to the partner
        rotate(poseStack, Axis.YP.rotationDegrees(bodyRot - 180));
        poseStack.translate((float) ((link.x() - x) * weight), 0, (float) ((link.z() - z) * weight));
        rotate(poseStack, Axis.YP.rotationDegrees(180 - yaw));
    }

    // applies the whole-body (body bone) transform, call right after the renderer's setupRotations
    public static void poseBody(PoseStack poseStack, EmotePlayback.Frame frame) {
        frame.animation().sample(frame.seconds(), POSE);
        if (!POSE.hasRotation(Part.BODY) && !POSE.hasPosition(Part.BODY)) {
            return;
        }

        float weight = frame.weight();
        float scale = PLAYER_MODEL_SCALE;
        double pivot = PoseSolver.BODY_PIVOT_Y * scale;
        poseStack.translate(
                POSE.position(Part.BODY, 0) * weight * scale,
                POSE.position(Part.BODY, 1) * weight * scale + pivot,
                POSE.position(Part.BODY, 2) * weight * scale);
        rotate(poseStack, Axis.ZP.rotation((float) (POSE.rotation(Part.BODY, 2) * weight)));
        rotate(poseStack, Axis.YP.rotation((float) (POSE.rotation(Part.BODY, 1) * weight)));
        rotate(poseStack, Axis.XP.rotation((float) (POSE.rotation(Part.BODY, 0) * weight)));
        poseStack.translate(0, -pivot, 0);
    }

    //? if <1.21.2 {
    // model parts are shared by every player drawn with the same renderer and vanilla does not reset all values each
    // frame, so undo what an emote may have changed on the previous player
    public static void resetPose(HumanoidModel<?> model) {
        model.head.x = 0;
        model.head.z = 0;
        model.head.zRot = 0;
        model.body.x = 0;
        model.body.z = 0;
        model.body.zRot = 0;
        model.rightLeg.x = -1.9F;
        model.leftLeg.x = 1.9F;
    }
    //?}

    // moves the pose stack like the emote moves the torso of a standing player, for layers drawn relative to the whole
    // body that should bend with the torso: the elytra, and the cape before 1.21.2
    public static void followTorso(PoseStack poseStack, @Nullable EmotePlayback.Frame frame) {
        if (frame == null) {
            return;
        }

        float[] torso = SOLVER.parts[Part.TORSO.ordinal()];
        torso[0] = Part.TORSO.originX;
        torso[1] = Part.TORSO.originY;
        torso[2] = Part.TORSO.originZ;
        torso[3] = torso[4] = torso[5] = 0;
        frame.animation().sample(frame.seconds(), POSE);
        SOLVER.solve(POSE, frame.look(), frame.weight());
        // same as ModelPart#translateAndRotate
        poseStack.translate(torso[0] / 16F, torso[1] / 16F, torso[2] / 16F);
        if (torso[3] != 0 || torso[4] != 0 || torso[5] != 0) {
            rotate(poseStack, new Quaternionf().rotationZYX(torso[5], torso[4], torso[3]));
        }
    }

    // world position of a locator of an emoting player: a bone's far end (hands, feet, head center, torso center) in the
    // emote's pose, unknown or missing locators mean the torso
    public static Vec3 locatorPosition(Player player, EmotePlayback.Frame frame, @Nullable String locator) {
        Part part = locatorPart(locator);
        // the emote over a standing player, good enough for where an effect starts
        for (Part modelPart : Part.MODEL_PARTS) {
            float[] values = SOLVER.parts[modelPart.ordinal()];
            values[0] = modelPart.originX;
            values[1] = modelPart.originY;
            values[2] = modelPart.originZ;
            values[3] = values[4] = values[5] = 0;
        }

        frame.animation().sample(frame.seconds(), POSE);
        SOLVER.solve(POSE, frame.look(), frame.weight());

        // the transforms the player renderer applies, see PlayerRendererMixin
        PoseStack stack = new PoseStack();
        PartnerLink link = frame.link();
        // partner emotes draw the player at their spot, see alignPartner
        float bodyYaw = link != null ? link.yaw() : player.yBodyRot;
        rotate(stack, Axis.YP.rotationDegrees(180 - bodyYaw));
        poseBody(stack, frame);
        stack.scale(-1, -1, 1);
        stack.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
        stack.translate(0, -1.501F, 0);
        float[] values = SOLVER.parts[part.ordinal()];
        stack.translate(values[0] / 16F, values[1] / 16F, values[2] / 16F);
        rotate(stack, new Quaternionf().rotationZYX(values[5], values[4], values[3]));
        float[] end = LOCATOR_ENDS[part.ordinal()];
        Vector3f point = stack.last().pose().transformPosition(end[0] / 16F, end[1] / 16F, end[2] / 16F, new Vector3f());
        double baseX = link != null ? link.x() : player.getX();
        double baseZ = link != null ? link.z() : player.getZ();
        return new Vec3(baseX + point.x(), player.getY() + point.y(), baseZ + point.z());
    }

    private static void rotate(PoseStack poseStack, Quaternionf rotation) {
        //? if >=26.3 {
        /*poseStack.rotate(rotation);
        *///?} else
        poseStack.mulPose(rotation);
    }

    private static Part locatorPart(@Nullable String locator) {
        if (locator == null) {
            return Part.TORSO;
        }

        String name = locator.toLowerCase(Locale.ROOT);
        boolean right = name.contains("right");
        boolean left = name.contains("left");
        if (name.contains("arm") || name.contains("hand")) {
            return right ? Part.RIGHT_ARM : left ? Part.LEFT_ARM : Part.TORSO;
        }

        if (name.contains("leg") || name.contains("foot")) {
            return right ? Part.RIGHT_LEG : left ? Part.LEFT_LEG : Part.TORSO;
        }

        if (name.contains("head") || name.contains("mouth") || name.contains("eye")) {
            return Part.HEAD;
        }

        Part part = Part.byBoneName(name);
        return part == null || part == Part.BODY ? Part.TORSO : part;
    }

    // model parts in Part#MODEL_PARTS order
    private static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[]{model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }
}
