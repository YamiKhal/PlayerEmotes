package com.yamikhal.playeremotes.client.preview;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.gui.Canvas;
import net.minecraft.resources.ResourceLocation;

//? if <1.21.9 {
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yamikhal.playeremotes.client.animation.EmoteProps;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import com.yamikhal.playeremotes.network.AnimatedProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.2
/*import net.minecraft.client.renderer.item.ItemStackRenderState;*/
//?}

// draws a posed player model into a GUI rectangle, clipped to it. before 1.21.9 drawn directly, from then on GUI
// rendering is deferred and it goes through PreviewPictures
public final class PreviewRenderer {

    // shaded white skin, when there is no local player to take the skin from
    public static final ResourceLocation SKIN = PlayerEmotes.id("textures/entity/preview_skin.png");
    // pixels per block, relative to preview height
    static final float SCALE = 0.44F;
    // turns the model a bit away from the camera so it looks 3D
    static final float YAW = 25;
    // gap below the feet, relative to preview height
    static final float FLOOR = 0.07F;

    //? if <1.21.9 {
    //? if >=1.21.2
    /*private static final ItemStackRenderState PROP_ITEM = new ItemStackRenderState();*/
    // wide and slim arms
    //? if >=1.21.2 {
    /*private static final PlayerModel[] MODELS = new PlayerModel[2];
    *///?} else
    private static final PlayerModel<?>[] MODELS = new PlayerModel<?>[2];
    //?}

    private PreviewRenderer() {}

    public static void draw(Canvas canvas, EmotePlayback.Frame frame, int x0, int y0, int x1, int y1) {
        float scale = (y1 - y0) * SCALE;
        //? if >=1.21.9 {
        /*PreviewPictures.submit(canvas, frame, x0, y0, x1, y1, scale);
        *///?} else {
        canvas.scissor(x0, y0, x1, y1);
        PoseStack pose = canvas.graphics.pose();
        pose.pushPose();
        // feet near the bottom edge, GUI y points down so the model gets flipped upright
        pose.translate((x0 + x1) / 2F, y1 - (y1 - y0) * FLOOR, 100);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(-YAW));
        // transforms LivingEntityRenderer applies after setupRotations
        EmoteRenderer.poseBody(pose, frame);
        pose.scale(-1, -1, 1);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0, -1.501F, 0);

        AbstractClientPlayer player = Minecraft.getInstance().player;
        ResourceLocation texture = player != null ? skinTexture(player) : SKIN;
        var model = model(player != null && isSlim(player));
        //? if >=1.21.2 {
        /*model.resetPose();
        *///?} else {
        for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            part.resetPose();
        }
        //?}
        EmoteRenderer.poseModel(model, frame);
        showOuterLayers(model, player);

        PreviewLighting.setup();
        //? if >=1.21.2 {
        /*canvas.graphics.drawSpecial(buffers -> model.renderToBuffer(pose, buffers.getBuffer(model.renderType(texture)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
        *///?} else {
        var buffer = canvas.graphics.bufferSource().getBuffer(model.renderType(texture));
        //? if >=1.21 {
        model.renderToBuffer(pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        //?} else
        /*model.renderToBuffer(pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);*/
        canvas.graphics.flush();
        //?}
        drawProps(canvas, model, frame, player);
        Lighting.setupFor3DItems();
        pose.popPose();
        canvas.endScissor();
        //?}
    }

    //? if <1.21.9 {
    // items of the emote where ItemInHandLayerMixin puts them on players, pose stack in model space
    //? if >=1.21.2 {
    /*private static void drawProps(Canvas canvas, PlayerModel model, EmotePlayback.Frame frame, @Nullable AbstractClientPlayer player) {
    *///?} else
    private static void drawProps(Canvas canvas, PlayerModel<?> model, EmotePlayback.Frame frame, @Nullable AbstractClientPlayer player) {
        PoseStack pose = canvas.graphics.pose();
        Minecraft minecraft = Minecraft.getInstance();
        for (AnimatedProp prop : EmoteProps.previewProps(frame)) {
            ItemStack item = EmoteProps.previewStack(prop, player);
            if (item.isEmpty()) continue;

            ItemDisplayContext context = EmoteProps.context(prop, player);
            boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            pose.pushPose();
            if (EmoteProps.place(pose, model, frame, prop, model::translateToHand)) {
                //? if >=1.21.2 {
                /*minecraft.getItemModelResolver().updateForTopItem(PROP_ITEM, item, context, left, minecraft.level, player, 0);
                canvas.graphics.drawSpecial(buffers -> PROP_ITEM.render(pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
                *///?} else {
                minecraft.getItemRenderer().renderStatic(player, item, context, left, pose, canvas.graphics.bufferSource(),
                        minecraft.level, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                canvas.graphics.flush();
                //?}
            }

            pose.popPose();
        }
    }

    private static ResourceLocation skinTexture(AbstractClientPlayer player) {
        //? if >=1.20.2 {
        return player.getSkin().texture();
        //?} else
        /*return player.getSkinTextureLocation();*/
    }

    private static boolean isSlim(AbstractClientPlayer player) {
        //? if >=1.20.2 {
        return player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        //?} else
        /*return "slim".equals(player.getModelName());*/
    }

    //? if >=1.21.2 {
    /*private static PlayerModel model(boolean slim) {
    *///?} else
    private static PlayerModel<?> model(boolean slim) {
        int index = slim ? 1 : 0;
        if (MODELS[index] == null) {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
            //? if >=1.21.2 {
            /*MODELS[index] = new PlayerModel(root, slim);
            *///?} else {
            MODELS[index] = new PlayerModel<>(root, slim);
            // models start as baby models until an entity renderer says otherwise
            MODELS[index].young = false;
            //?}
        }

        return MODELS[index];
    }

    // shows the skin's outer layer like the player set it up, fallback skin has none
    //? if >=1.21.2 {
    /*private static void showOuterLayers(PlayerModel model, @Nullable AbstractClientPlayer player) {
    *///?} else
    private static void showOuterLayers(PlayerModel<?> model, @Nullable AbstractClientPlayer player) {
        model.hat.visible = player != null && player.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = player != null && player.isModelPartShown(PlayerModelPart.JACKET);
        model.leftSleeve.visible = player != null && player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = player != null && player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = player != null && player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = player != null && player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        //? if <1.21.2 {
        // before 1.21.2 outer layer parts are separate and follow the inner ones in setupAnim
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        //?}
    }
    //?}
}
