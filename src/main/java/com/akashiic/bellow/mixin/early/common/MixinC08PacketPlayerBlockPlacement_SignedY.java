package com.akashiic.bellow.mixin.early.common;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Y as signed short (vanilla: unsigned byte). Direction 255 still means "use item in air". */
@Mixin(C08PacketPlayerBlockPlacement.class)
public abstract class MixinC08PacketPlayerBlockPlacement_SignedY {

    @Shadow
    private int field_149583_a;
    @Shadow
    private int field_149581_b;
    @Shadow
    private int field_149582_c;
    @Shadow
    private int field_149579_d;
    @Shadow
    private ItemStack field_149580_e;
    @Shadow
    private float field_149577_f;
    @Shadow
    private float field_149578_g;
    @Shadow
    private float field_149584_h;

    @Inject(method = "readPacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$read(PacketBuffer data, CallbackInfo ci) throws IOException {
        this.field_149583_a = data.readInt();
        this.field_149581_b = data.readShort();
        this.field_149582_c = data.readInt();
        this.field_149579_d = data.readUnsignedByte();
        this.field_149580_e = data.readItemStackFromBuffer();
        this.field_149577_f = (float) data.readUnsignedByte() / 16.0F;
        this.field_149578_g = (float) data.readUnsignedByte() / 16.0F;
        this.field_149584_h = (float) data.readUnsignedByte() / 16.0F;
        ci.cancel();
    }

    @Inject(method = "writePacketData", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$write(PacketBuffer data, CallbackInfo ci) throws IOException {
        data.writeInt(this.field_149583_a);
        data.writeShort(this.field_149581_b);
        data.writeInt(this.field_149582_c);
        data.writeByte(this.field_149579_d);
        data.writeItemStackToBuffer(this.field_149580_e);
        data.writeByte((int) (this.field_149577_f * 16.0F));
        data.writeByte((int) (this.field_149578_g * 16.0F));
        data.writeByte((int) (this.field_149584_h * 16.0F));
        ci.cancel();
    }
}
