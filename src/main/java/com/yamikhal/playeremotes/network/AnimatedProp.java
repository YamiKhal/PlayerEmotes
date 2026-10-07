package com.yamikhal.playeremotes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// item moved by its own bone of the animation (sword thrown up, chair to sit on). bone is the Blockbench bone name,
// item the item to show for ITEM, otherwise the one the player holds (source). attach is what the bone hangs on,
// display how the item model is drawn
public record AnimatedProp(String bone, Source source, @Nullable ResourceLocation item, Attach attach, Display display) {

    public static final int MAX_PROPS = 8;
    public static final int MAX_BONE_LENGTH = 64;
    private static final int MAX_ID_LENGTH = 256;

    public enum Source {
        ITEM,
        HELD_RIGHT,
        HELD_LEFT,
        HELD_MAINHAND,
        HELD_OFFHAND
    }

    public enum Attach {
        RIGHT_HAND,
        LEFT_HAND,
        BODY,
        HEAD,
        // the ground under the player, turns with the player but not with the body bone
        ROOT;

        public boolean isHand() {
            return this == RIGHT_HAND || this == LEFT_HAND;
        }
    }

    public enum Display {
        // hand for props on a hand, none for the rest
        AUTO,
        HAND,
        NONE,
        FIXED,
        GROUND,
        HEAD
    }

    public AnimatedProp {
        bone = bone.toLowerCase(Locale.ROOT);
        if (source == Source.ITEM && item == null) {
            throw new IllegalArgumentException("prop '" + bone + "' has no item");
        }
    }

    // item the player holds, not one of its own
    public boolean isHeld() {
        return this.source != Source.ITEM;
    }

    static void writeList(FriendlyByteBuf buf, List<AnimatedProp> props) {
        int count = Math.min(props.size(), MAX_PROPS);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            props.get(i).write(buf);
        }
    }

    static List<AnimatedProp> readList(FriendlyByteBuf buf) {
        int count = Math.min(buf.readUnsignedByte(), MAX_PROPS);
        if (count == 0) {
            return List.of();
        }

        List<AnimatedProp> props = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AnimatedProp prop = read(buf);
            if (prop != null) {
                props.add(prop);
            }
        }

        return List.copyOf(props);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.bone, MAX_BONE_LENGTH);
        buf.writeByte(this.source.ordinal());
        buf.writeByte(this.attach.ordinal());
        buf.writeByte(this.display.ordinal());
        if (this.source == Source.ITEM) {
            buf.writeUtf(this.item.toString(), MAX_ID_LENGTH);
        }
    }

    // null if broken, everything still read so the next prop lines up
    @Nullable
    private static AnimatedProp read(FriendlyByteBuf buf) {
        String bone = buf.readUtf(MAX_BONE_LENGTH);
        Source source = byId(Source.values(), buf.readUnsignedByte());
        Attach attach = byId(Attach.values(), buf.readUnsignedByte());
        Display display = byId(Display.values(), buf.readUnsignedByte());
        ResourceLocation item = null;
        if (source == Source.ITEM) {
            item = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
            if (item == null) {
                return null;
            }
        }

        return new AnimatedProp(bone, source, item, attach, display);
    }

    // unknown ids (newer version) fall back to the first value
    private static <E> E byId(E[] values, int id) {
        return id < values.length ? values[id] : values[0];
    }
}
