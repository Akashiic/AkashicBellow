package com.akashiic.bellow.mixin.early.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Below the horizon the sky draws a black box under the camera that reaches down to the bottom of the world:
 * {@code f9 = -(d0 + 65)} with {@code d0 = eyeY - 63}, i.e. down to Y -2. Below that height the box turns upside down
 * and its walls, one block away, cover the whole sky but a small square at the zenith. Measured from the floor it
 * stays under the camera. Applied with any terrain renderer (Angelica keeps the vanilla sky).
 */
@Mixin(value = RenderGlobal.class, priority = 1100)
public abstract class MixinRenderGlobal_DeepSky {

    @Shadow
    private WorldClient theWorld;

    // the only 65.0D of renderSky
    @ModifyConstant(method = "renderSky(F)V", constant = @Constant(doubleValue = 65.0D), require = 1, allow = 1)
    private double bellow$voidBoxToFloor(double sixtyFive) {
        return this.theWorld == null ? sixtyFive : sixtyFive - BellowAPI.getMinY(this.theWorld);
    }
}
