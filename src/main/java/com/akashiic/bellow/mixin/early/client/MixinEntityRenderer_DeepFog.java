package com.akashiic.bellow.mixin.early.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Near the bottom of the world the fog colour fades to black and the void fog closes in to 5 blocks (survival, worlds
 * with void particles): both are measured from the floor instead of from Y 0.
 */
@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class MixinEntityRenderer_DeepFog {

    @Shadow
    @Final
    private Minecraft mc;

    // double d0 = interpolatedY * provider.getVoidFogYFactor(); (first double local of the method)
    @ModifyVariable(
        method = "updateFogColor(F)V",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        require = 1,
        allow = 1)
    private double bellow$voidFromFloor(double d0) {
        if (this.mc.theWorld == null) return d0;
        int floor = BellowAPI.getMinY(this.mc.theWorld);
        return floor == 0 ? d0 : d0 - floor * this.mc.theWorld.provider.getVoidFogYFactor();
    }

    // setupFog: d0 = skyLight / 16 + (interpolatedY + 4) / 32; the only 4.0D of the method.
    @ModifyConstant(method = "setupFog(IF)V", constant = @Constant(doubleValue = 4.0D), require = 1, allow = 1)
    private double bellow$voidFogFromFloor(double four) {
        return this.mc.theWorld == null ? four : four - BellowAPI.getMinY(this.mc.theWorld);
    }
}
