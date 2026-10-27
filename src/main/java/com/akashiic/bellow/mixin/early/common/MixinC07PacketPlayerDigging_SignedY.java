package com.akashiic.bellow.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Y travels as an unsigned byte in vanilla; widen it to a signed short. Both sides run the mod (FML handshake). */
@Mixin(C07PacketPlayerDigging.class)
public abstract class MixinC07PacketPlayerDigging_SignedY {

    @Shadow
    private int field_149511_a;
    @Shadow
    private int field_149509_b;
    @Shadow
    private int field_149510_c;
    @Shadow
    private int field_149507_d;
    @Shadow
    private int field_149508_e;

    @Inject(method = "readPacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$read(PacketBuffer data, CallbackInfo ci) {
        this.field_149508_e = data.readUnsignedByte();
        this.field_149511_a = data.readInt();
        this.field_149509_b = data.readShort();
        this.field_149510_c = data.readInt();
        this.field_149507_d = data.readUnsignedByte();
        ci.cancel();
    }

    @Inject(method = "writePacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$write(PacketBuffer data, CallbackInfo ci) {
        data.writeByte(this.field_149508_e);
        data.writeInt(this.field_149511_a);
        data.writeShort(this.field_149509_b);
        data.writeInt(this.field_149510_c);
        data.writeByte(this.field_149507_d);
        ci.cancel();
    }
}
