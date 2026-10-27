package com.akashiic.bellow.mixin.early.client;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.IDeepWorld;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * "depthsuspend" particles appear when {@code rand(8) > y}: count from the floor so deep caves are not full of them.
 */
@Mixin(value = WorldClient.class, priority = 1100)
public abstract class MixinWorldClient_DeepParticles {

    @Expression("? > ?")
    @ModifyExpressionValue(
        method = "doVoidFogParticles(III)V",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$fromFloor(boolean original, @Local(type = int.class, ordinal = 5) int y) {
        int floor = ((IDeepWorld) (Object) this).bellow$getMinY();
        return floor == 0 ? original : original && y - floor < 8;
    }
}
