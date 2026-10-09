package com.yamikhal.playeremotes.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yamikhal.playeremotes.anim.Pose;
import com.yamikhal.playeremotes.network.AnimatedProp;
import com.yamikhal.playeremotes.network.EmoteProp;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

// items of emotes, render thread only:
// - prop (emote "item"), shown instead of what a hand really holds
// - animated props (emote "props"), drawn on their own bone, a held item moved this way leaves its hand empty
public final class EmoteProps {

    private static final Pose CONTEXT = new Pose();
    // position, rotation, scale of the prop bone
    private static final double[] BONE = new double[9];
    private static final Map<ResourceLocation, ItemStack> PREVIEW_ITEMS = new HashMap<>();

    private EmoteProps() {}

    // null keeps the real item, empty hides it
    @Nullable
    public static ItemStack propFor(LivingEntity entity, HumanoidArm arm) {
        EmotePlayback playback = playback(entity);
        if (playback == null) {
            return null;
        }

        EmoteProp prop = playback.options().prop();
        if (!playback.isStopping() && prop != null && (arm == HumanoidArm.RIGHT ? prop.hand().right() : prop.hand().left())) {
            if (playback.propStack == null) {
                playback.propStack = item(prop.item());
            }

            return playback.propStack;
        }

        // no animation (server pack not downloaded yet) means no prop drawn, hand keeps its item
        if (playback.options().props().isEmpty() || AnimationRegistry.get(playback.animationId()) == null) {
            return null;
        }

        for (AnimatedProp animated : playback.options().props()) {
            if (animated.source() == AnimatedProp.Source.NONE) continue;

            if (hand(animated.attach()) == arm || heldArm(entity, animated.source()) == arm) {
                return ItemStack.EMPTY;
            }
        }

        return null;
    }

    // animated props of the entity's emote, empty if none
    public static List<AnimatedProp> props(LivingEntity entity) {
        EmotePlayback playback = playback(entity);
        return playback == null ? List.of() : playback.options().props();
    }

    // item the prop shows, empty for none (empty hand, unknown item)
    public static ItemStack stack(LivingEntity entity, int index) {
        EmotePlayback playback = playback(entity);
        if (playback == null || index >= playback.options().props().size()) {
            return ItemStack.EMPTY;
        }

        AnimatedProp prop = playback.options().props().get(index);
        if (prop.source() == AnimatedProp.Source.NONE) {
            return ItemStack.EMPTY;
        }

        if (prop.isHeld()) {
            return heldArm(entity, prop.source()) == entity.getMainArm() ? entity.getMainHandItem() : entity.getOffhandItem();
        }

        if (playback.propStacks == null) {
            playback.propStacks = new ItemStack[playback.options().props().size()];
        }

        if (playback.propStacks[index] == null) {
            playback.propStacks[index] = item(prop.item());
        }

        return playback.propStacks[index];
    }

    // what a preview draws: the animated props, and the prop as props resting in its hands
    public static List<AnimatedProp> previewProps(EmotePlayback.Frame frame) {
        EmoteProp prop = frame.prop();
        if (prop == null) {
            return frame.props();
        }

        // no bone of that name, so they rest where the hand holds items
        List<AnimatedProp> props = new ArrayList<>(frame.props());
        if (prop.hand().right()) {
            props.add(handProp(prop, AnimatedProp.Attach.RIGHT_HAND));
        }

        if (prop.hand().left()) {
            props.add(handProp(prop, AnimatedProp.Attach.LEFT_HAND));
        }

        return props.size() > AnimatedProp.MAX_PROPS ? props.subList(0, AnimatedProp.MAX_PROPS) : props;
    }

    // item a preview shows, held items from the local player
    public static ItemStack previewStack(AnimatedProp prop, @Nullable LivingEntity player) {
        if (prop.source() == AnimatedProp.Source.NONE) {
            return ItemStack.EMPTY;
        }

        if (!prop.isHeld()) {
            return PREVIEW_ITEMS.computeIfAbsent(prop.item(), EmoteProps::item);
        }

        if (player == null) {
            return ItemStack.EMPTY;
        }

        return heldArm(player, prop.source()) == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
    }

    // holder picks the side of held items drawn hand style away from the hands, null means right
    public static ItemDisplayContext context(AnimatedProp prop, @Nullable LivingEntity holder) {
        return switch (prop.display()) {
            case AUTO -> prop.attach().isHand() ? handContext(prop, holder) : ItemDisplayContext.NONE;
            case HAND -> handContext(prop, holder);
            case NONE -> ItemDisplayContext.NONE;
            case FIXED -> ItemDisplayContext.FIXED;
            case GROUND -> ItemDisplayContext.GROUND;
            case HEAD -> ItemDisplayContext.HEAD;
        };
    }

    // moves the pose stack (model space, as render layers get it) onto the bone of props[index], false if scaled to
    // nothing. toHand is the model's translateToHand. rest pose: on a hand where vanilla holds items, else the item
    // model centered on the bone pivot (see the Blockbench templates)
    public static boolean place(PoseStack poseStack, HumanoidModel<?> model, EmotePlayback.Frame frame, List<AnimatedProp> props,
                                int index, BiConsumer<HumanoidArm, PoseStack> toHand) {
        AnimatedProp prop = props.get(index);
        if (!placeBone(poseStack, model, frame, props, index, toHand)) {
            return false;
        }

        if (prop.attach().isHand()) {
            // ItemInHandLayer's turn, item points forward out of the fist
            rotate(poseStack, Axis.XP.rotationDegrees(-90));
            rotate(poseStack, Axis.YP.rotationDegrees(180));
        } else {
            // model space is upside down
            rotate(poseStack, Axis.ZP.rotationDegrees(180));
        }

        return true;
    }

    // parents first, each one moves onto its pivot and applies its animation
    private static boolean placeBone(PoseStack poseStack, HumanoidModel<?> model, EmotePlayback.Frame frame, List<AnimatedProp> props,
                                     int index, BiConsumer<HumanoidArm, PoseStack> toHand) {
        AnimatedProp prop = props.get(index);
        if (prop.parent() >= 0) {
            if (!placeBone(poseStack, model, frame, props, prop.parent(), toHand)) {
                return false;
            }
        } else {
            // attach point, in pixels of the frame the bone hangs on
            switch (prop.attach()) {
                case RIGHT_HAND, LEFT_HAND -> {
                    HumanoidArm arm = hand(prop.attach());
                    toHand.accept(arm, poseStack);
                    // where ItemInHandLayer puts the item, before its own turn
                    poseStack.translate((arm == HumanoidArm.LEFT ? 1 : -1) / 16F, 10 / 16F, -2 / 16F);
                }
                case BODY -> {
                    model.body.translateAndRotate(poseStack);
                    poseStack.translate(0, 6 / 16F, 0);
                }
                case HEAD -> {
                    model.head.translateAndRotate(poseStack);
                    poseStack.translate(0, -4 / 16F, 0);
                }
                case ROOT -> {
                    EmoteRenderer.undoBody(poseStack, frame);
                    poseStack.translate(0, 16 / 16F, 0);
                }
            }
        }

        frame.sampleProp(prop.bone(), CONTEXT, BONE);
        // hand props blend from the held item spot, the rest keep their pose, blending would slide them to their pivot
        float weight = prop.attach().isHand() ? frame.weight() : 1;
        // same as a skeleton bone (see Pose): y flipped, rotation ZYX. pivot stays 0 unless pack.json nests props, a
        // top prop rests on it, a nested one only turns around it (its item stays where the top prop rests)
        Vec3 pivot = prop.pivot();
        poseStack.translate((float) ((pivot.x + BONE[0] * weight) / 16), (float) ((-pivot.y - BONE[1] * weight) / 16),
                (float) ((pivot.z + BONE[2] * weight) / 16));
        float rx = (float) Math.toRadians(BONE[3] * weight);
        float ry = (float) Math.toRadians(BONE[4] * weight);
        float rz = (float) Math.toRadians(BONE[5] * weight);
        if (rx != 0 || ry != 0 || rz != 0) {
            rotate(poseStack, new Quaternionf().rotationZYX(rz, ry, rx));
        }

        float sx = (float) (1 + (BONE[6] - 1) * weight);
        float sy = (float) (1 + (BONE[7] - 1) * weight);
        float sz = (float) (1 + (BONE[8] - 1) * weight);
        if (Math.abs(sx * sy * sz) < 1.0E-6F) {
            return false;
        }

        poseStack.scale(sx, sy, sz);
        if (prop.parent() >= 0) {
            poseStack.translate((float) (-pivot.x / 16), (float) (pivot.y / 16), (float) (-pivot.z / 16));
        }

        return true;
    }

    @Nullable
    private static EmotePlayback playback(LivingEntity entity) {
        return entity instanceof Player ? EmotePlayers.get(entity.getUUID()) : null;
    }

    private static AnimatedProp handProp(EmoteProp prop, AnimatedProp.Attach hand) {
        return new AnimatedProp("", AnimatedProp.Source.ITEM, prop.item(), hand, AnimatedProp.Display.AUTO, -1, Vec3.ZERO);
    }

    private static ItemStack item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ItemDisplayContext handContext(AnimatedProp prop, @Nullable LivingEntity holder) {
        boolean left = prop.attach() == AnimatedProp.Attach.LEFT_HAND
                || (!prop.attach().isHand() && holder != null && heldArm(holder, prop.source()) == HumanoidArm.LEFT);
        return left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Nullable
    private static HumanoidArm hand(AnimatedProp.Attach attach) {
        return switch (attach) {
            case RIGHT_HAND -> HumanoidArm.RIGHT;
            case LEFT_HAND -> HumanoidArm.LEFT;
            default -> null;
        };
    }

    // arm whose item a held prop takes, null for own items
    @Nullable
    private static HumanoidArm heldArm(LivingEntity entity, AnimatedProp.Source source) {
        return switch (source) {
            case ITEM, NONE -> null;
            case HELD_RIGHT -> HumanoidArm.RIGHT;
            case HELD_LEFT -> HumanoidArm.LEFT;
            case HELD_MAINHAND -> entity.getMainArm();
            case HELD_OFFHAND -> entity.getMainArm().getOpposite();
        };
    }

    private static void rotate(PoseStack poseStack, Quaternionf rotation) {
        //? if >=26.3 {
        /*poseStack.rotate(rotation);
        *///?} else
        poseStack.mulPose(rotation);
    }
}
