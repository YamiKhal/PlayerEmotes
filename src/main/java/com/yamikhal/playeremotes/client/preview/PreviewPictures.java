package com.yamikhal.playeremotes.client.preview;

//? if >=1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmoteProps;
import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import com.yamikhal.playeremotes.client.gui.Canvas;
import com.yamikhal.playeremotes.mixin.client.GuiGraphicsAccessor;
import com.yamikhal.playeremotes.network.AnimatedProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
//? if >=26.1 {
/^import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
^///?} else {
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
//?}
//? if >=26.3 {
/^import net.minecraft.client.renderer.SubmitNodeCollector;
^///?} else
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

// draws previews through the deferred GUI renderer (1.21.9+). a vanilla picture-in-picture renderer shows only
// one element per frame since its elements share one texture, so every preview gets its own state class and
// renderer from a fixed pool
public final class PreviewPictures {

    // fallback without a local player to take the skin from
    private static final PlayerSkin FALLBACK_SKIN = PlayerSkin.insecure(
            new ClientAsset.ResourceTexture(PlayerEmotes.id("entity/preview_skin"), PreviewRenderer.SKIN),
            null, null, PlayerModelType.WIDE);
    private static final List<Supplier<State>> STATES = List.of(S0::new, S1::new, S2::new, S3::new, S4::new, S5::new, S6::new, S7::new, S8::new, S9::new, S10::new, S11::new, S12::new, S13::new, S14::new, S15::new, S16::new, S17::new, S18::new, S19::new, S20::new, S21::new, S22::new, S23::new, S24::new, S25::new, S26::new, S27::new, S28::new, S29::new, S30::new, S31::new, S32::new, S33::new, S34::new, S35::new, S36::new, S37::new, S38::new, S39::new);
    // next free pool entry this frame
    private static int next;

    private PreviewPictures() {}

    // adds the pool's renderers to the vanilla ones, while the game renderer is created
    public static List<PictureInPictureRenderer<?>> withRenderers(List<PictureInPictureRenderer<?>> vanilla) {
        List<PictureInPictureRenderer<?>> all = new ArrayList<>(vanilla);
        for (Supplier<State> state : STATES) {
            all.add(new Renderer(state.get().getClass()));
        }

        return all;
    }

    public static void beginFrame() {
        next = 0;
    }

    static void submit(Canvas canvas, EmotePlayback.Frame frame, int x0, int y0, int x1, int y1, float scale) {
        int[] clip = Canvas.currentScissor();
        ScreenRectangle scissor = clip == null ? null : new ScreenRectangle(clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1]);
        ScreenRectangle bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
        if (bounds == null || next >= STATES.size()) {
            return;
        }

        AvatarRenderState avatar = new AvatarRenderState();
        LocalPlayer player = Minecraft.getInstance().player;
        // skin picks the wide or slim model, fallback skin has no outer layer
        avatar.skin = player != null ? player.getSkin() : FALLBACK_SKIN;
        avatar.showHat = player != null && player.isModelPartShown(PlayerModelPart.HAT);
        avatar.showJacket = player != null && player.isModelPartShown(PlayerModelPart.JACKET);
        avatar.showLeftSleeve = player != null && player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        avatar.showRightSleeve = player != null && player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        avatar.showLeftPants = player != null && player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        avatar.showRightPants = player != null && player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        avatar.showCape = false;
        avatar.bodyRot = 180 + PreviewRenderer.YAW;
        avatar.boundingBoxWidth = 0.6F;
        avatar.boundingBoxHeight = 1.8F;
        ((EmoteRenderState) avatar).playeremotes$setFrame(frame);
        setProps(avatar, frame, player);

        State state = STATES.get(next++).get();
        state.avatar = avatar;
        state.x0 = x0;
        state.y0 = y0;
        state.x1 = x1;
        state.y1 = y1;
        state.scale = scale;
        state.scissor = scissor;
        state.bounds = bounds;
        //? if >=26.1 {
        /^((GuiGraphicsAccessor) (Object) canvas.graphics).playeremotes$guiRenderState().addPicturesInPictureState(state);
        ^///?} else
        ((GuiGraphicsAccessor) (Object) canvas.graphics).playeremotes$guiRenderState().submitPicturesInPictureState(state);
    }

    // items of the emote, the avatar renderer draws them like on players (see ItemInHandLayerMixin)
    private static void setProps(AvatarRenderState avatar, EmotePlayback.Frame frame, @Nullable LocalPlayer player) {
        EmoteRenderState emote = (EmoteRenderState) avatar;
        List<AnimatedProp> props = EmoteProps.previewProps(frame);
        emote.playeremotes$setProps(props);
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < props.size(); i++) {
            AnimatedProp prop = props.get(i);
            minecraft.getItemModelResolver().updateForTopItem(emote.playeremotes$propItem(i), EmoteProps.previewStack(prop, player),
                    EmoteProps.context(prop, player), minecraft.level, player, 0);
        }
    }

    static class State implements PictureInPictureRenderState {

        AvatarRenderState avatar;
        int x0;
        int y0;
        int x1;
        int y1;
        float scale;
        ScreenRectangle scissor;
        ScreenRectangle bounds;

        @Override
        public int x0() {
            return this.x0;
        }

        @Override
        public int y0() {
            return this.y0;
        }

        @Override
        public int x1() {
            return this.x1;
        }

        @Override
        public int y1() {
            return this.y1;
        }

        @Override
        public float scale() {
            return this.scale;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return this.scissor;
        }

        @Override
        public ScreenRectangle bounds() {
            return this.bounds;
        }
    }

    static final class S0 extends State {}

    static final class S1 extends State {}

    static final class S2 extends State {}

    static final class S3 extends State {}

    static final class S4 extends State {}

    static final class S5 extends State {}

    static final class S6 extends State {}

    static final class S7 extends State {}

    static final class S8 extends State {}

    static final class S9 extends State {}

    static final class S10 extends State {}

    static final class S11 extends State {}

    static final class S12 extends State {}

    static final class S13 extends State {}

    static final class S14 extends State {}

    static final class S15 extends State {}

    static final class S16 extends State {}

    static final class S17 extends State {}

    static final class S18 extends State {}

    static final class S19 extends State {}

    static final class S20 extends State {}

    static final class S21 extends State {}

    static final class S22 extends State {}

    static final class S23 extends State {}

    static final class S24 extends State {}

    static final class S25 extends State {}

    static final class S26 extends State {}

    static final class S27 extends State {}

    static final class S28 extends State {}

    static final class S29 extends State {}

    static final class S30 extends State {}

    static final class S31 extends State {}

    static final class S32 extends State {}

    static final class S33 extends State {}

    static final class S34 extends State {}

    static final class S35 extends State {}

    static final class S36 extends State {}

    static final class S37 extends State {}

    static final class S38 extends State {}

    static final class S39 extends State {}

    static final class Renderer extends PictureInPictureRenderer<State> {

        private final Class<State> type;

        @SuppressWarnings("unchecked")
        Renderer(Class<? extends State> type) {
            //? if <26.3
            super(Minecraft.getInstance().renderBuffers().bufferSource());
            this.type = (Class<State>) type;
        }

        @Override
        public Class<State> getRenderStateClass() {
            return this.type;
        }

        @Override
        protected String getTextureLabel() {
            return "playeremotes_preview";
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2F;
        }

        //? if >=26.3 {
        /^@Override
        protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
            Minecraft minecraft = Minecraft.getInstance();
            PreviewLighting.setup();
            place(poseStack);
            poseStack.rotate(new Quaternionf().rotateZ((float) Math.PI));
            minecraft.getEntityRenderDispatcher().submit(state.avatar, new CameraRenderState(), 0, 0, 0, poseStack, collector);
        }
        ^///?} else {
        @Override
        protected void renderToTexture(State state, PoseStack poseStack) {
            Minecraft minecraft = Minecraft.getInstance();
            PreviewLighting.setup();
            place(poseStack);
            poseStack.mulPose(new Quaternionf().rotateZ((float) Math.PI));
            FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
            minecraft.getEntityRenderDispatcher().submit(state.avatar, new CameraRenderState(), 0, 0, 0, poseStack, features.getSubmitNodeStorage());
            features.renderAllFeatures();
        }
        //?}

        // origin is the texture center, in blocks with y down, move it to the feet
        private static void place(PoseStack poseStack) {
            poseStack.translate(0, (0.5F - PreviewRenderer.FLOOR) / PreviewRenderer.SCALE, 0);
        }
    }
}
*///?}
