package com.akashiic.bellow.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.PathFinder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** getSafePoint walks down {@code while (y > 0)}: mobs could not path down into the deep layer. */
@Mixin(value = PathFinder.class, priority = 1100)
public abstract class MixinPathFinder_Deep {

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y > 0")
    @ModifyExpressionValue(method = "getSafePoint", at = @At("MIXINEXTRAS:EXPRESSION"), require = 2, allow = 2)
    private boolean bellow$aboveFloor(boolean original, @Local(argsOnly = true) Entity entity,
        @Local(argsOnly = true, ordinal = 1) int y) {
        return y > BellowAPI.getMinY(entity.worldObj);
    }
}
