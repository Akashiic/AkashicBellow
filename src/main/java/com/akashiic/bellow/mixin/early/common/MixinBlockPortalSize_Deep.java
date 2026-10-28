package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * The portal frame check walks down to the bottom of the frame only {@code while (y > 0)}: a portal whose interior
 * reaches Y 0 or below was measured with width 0, so it never lit or collapsed on the next neighbour update.
 */
@Mixin(targets = "net.minecraft.block.BlockPortal$Size")
public abstract class MixinBlockPortalSize_Deep {

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y > 0")
    @ModifyExpressionValue(method = "<init>", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$walkToFloor(boolean original, @Local(argsOnly = true) World world,
        @Local(type = int.class, argsOnly = true, ordinal = 1) int y) {
        return y > BellowAPI.getMinY(world);
    }
}
