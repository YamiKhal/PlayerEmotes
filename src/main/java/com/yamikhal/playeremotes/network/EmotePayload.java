package com.yamikhal.playeremotes.network;

//? if >=1.20.5 {
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// carries an encoded EmoteNetwork message (1.20.5+ payload system)
public record EmotePayload(Type<EmotePayload> type, byte[] data) implements CustomPacketPayload {

    public static final Type<EmotePayload> C2S = new Type<>(EmoteNetwork.C2S);
    public static final Type<EmotePayload> S2C = new Type<>(EmoteNetwork.S2C);
    public static final StreamCodec<FriendlyByteBuf, EmotePayload> C2S_CODEC = codec(C2S);
    public static final StreamCodec<FriendlyByteBuf, EmotePayload> S2C_CODEC = codec(S2C);

    private static StreamCodec<FriendlyByteBuf, EmotePayload> codec(Type<EmotePayload> type) {
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.data),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new EmotePayload(type, data);
                });
    }
}
//?}
