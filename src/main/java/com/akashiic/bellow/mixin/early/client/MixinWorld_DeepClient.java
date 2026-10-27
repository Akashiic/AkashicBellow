package com.akashiic.bellow.mixin.early.client;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.IDeepWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** Client-only {@code World.getSkyBlockTypeBrightness}: {@code if (y < 0) y = 0;}. */
@Mixin(value = World.class, priority = 1100)
public abstract class MixinWorld_DeepClient {

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getSkyBlockTypeBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < ((IDeepWorld) (Object) this).bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y = @(0)")
    @ModifyExpressionValue(
        method = "getSkyBlockTypeBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private int bellow$clamp(int zero) {
        return ((IDeepWorld) (Object) this).bellow$getMinY();
    }
}
