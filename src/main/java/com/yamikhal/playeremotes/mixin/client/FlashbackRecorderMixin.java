package com.yamikhal.playeremotes.mixin.client;

//? if >=1.20.5 {
import com.yamikhal.playeremotes.client.compat.FlashbackCompat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// Flashback's hook for mods to add state to recording snapshots (see FlashbackCompat). Flashback is optional,
// target may be missing and then nothing happens
@Pseudo
@Mixin(targets = "com.moulberry.flashback.record.Recorder", remap = false)
public abstract class FlashbackRecorderMixin {

    @Inject(method = "writeCustomSnapshot", at = @At("HEAD"), require = 0, remap = false)
    private void playeremotes$writeEmotes(Consumer<Packet<? super ClientGamePacketListener>> consumer, CallbackInfo ci) {
        FlashbackCompat.writeSnapshot(consumer);
    }
}
//?}
