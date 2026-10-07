package com.yamikhal.playeremotes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

// item held during an emote (cup for "drink", flower for "give") instead of what the hand holds
public record EmoteProp(ResourceLocation item, Hand hand) {

    private static final int MAX_ID_LENGTH = 256;

    public enum Hand {
        RIGHT,
        LEFT,
        BOTH;

        private static final Hand[] VALUES = values();

        static Hand byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : RIGHT;
        }

        public boolean right() {
            return this != LEFT;
        }

        public boolean left() {
            return this != RIGHT;
        }
    }

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.item.toString(), MAX_ID_LENGTH);
        buf.writeByte(this.hand.ordinal());
    }

    @Nullable
    static EmoteProp read(FriendlyByteBuf buf) {
        ResourceLocation item = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
        Hand hand = Hand.byId(buf.readUnsignedByte());
        return item == null ? null : new EmoteProp(item, hand);
    }
}
