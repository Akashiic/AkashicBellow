package com.akashiic.bellow.mixin.early.client;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.IDeepWorld;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * "depthsuspend" particles appear when {@code rand(8) > y}, a chance of {@code (7 - y) / 8} in the bottom 8 layers:
 * the height is counted from the floor, so the same gradient sits at the bottom of the deep layer instead of filling
 * every deep cave.
 */
@Mixin(value = WorldClient.class, priority = 1100)
public abstract class MixinWorldClient_DeepParticles {

    @Expression("? > @(?)")
    @ModifyExpressionValue(
        method = "doVoidFogParticles(III)V",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private int bellow$heightAboveFloor(int y) {
        return y - ((IDeepWorld) (Object) this).bellow$getMinY();
    }
}
