package com.akashiic.bellow.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S28PacketEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World effects (block break particles/sounds, doors, ...). Y as signed short (vanilla: unsigned byte). */
@Mixin(S28PacketEffect.class)
public abstract class MixinS28PacketEffect_SignedY {

    @Shadow
    private int field_149251_a;
    @Shadow
    private int field_149249_b;
    @Shadow
    private int field_149250_c;
    @Shadow
    private int field_149247_d;
    @Shadow
    private int field_149248_e;
    @Shadow
    private boolean field_149246_f;

    @Inject(method = "readPacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$read(PacketBuffer data, CallbackInfo ci) {
        this.field_149251_a = data.readInt();
        this.field_149250_c = data.readInt();
        this.field_149247_d = data.readShort();
        this.field_149248_e = data.readInt();
        this.field_149249_b = data.readInt();
        this.field_149246_f = data.readBoolean();
        ci.cancel();
    }

    @Inject(method = "writePacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$write(PacketBuffer data, CallbackInfo ci) {
        data.writeInt(this.field_149251_a);
        data.writeInt(this.field_149250_c);
        data.writeShort(this.field_149247_d);
        data.writeInt(this.field_149248_e);
        data.writeInt(this.field_149249_b);
        data.writeBoolean(this.field_149246_f);
        ci.cancel();
    }
}
