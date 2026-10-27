package com.akashiic.bellow.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0APacketUseBed;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bed position. Vanilla sends Y as a signed byte (-128..127): too small for floors below -128 or beds above 127. */
@Mixin(S0APacketUseBed.class)
public abstract class MixinS0APacketUseBed_SignedY {

    @Shadow
    private int field_149097_a;
    @Shadow
    private int field_149095_b;
    @Shadow
    private int field_149096_c;
    @Shadow
    private int field_149094_d;

    @Inject(method = "readPacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$read(PacketBuffer data, CallbackInfo ci) {
        this.field_149097_a = data.readInt();
        this.field_149095_b = data.readInt();
        this.field_149096_c = data.readShort();
        this.field_149094_d = data.readInt();
        ci.cancel();
    }

    @Inject(method = "writePacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$write(PacketBuffer data, CallbackInfo ci) {
        data.writeInt(this.field_149097_a);
        data.writeInt(this.field_149095_b);
        data.writeShort(this.field_149096_c);
        data.writeInt(this.field_149094_d);
        ci.cancel();
    }
}
