package com.akashiic.bellow.mixin.early.common;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * {@code if (j1 <= 0 || j1 >= world.getHeight()) return false;} in canExtend/tryExtend. Locals: x, y, z, side are the
 * int parameters (ordinals 0..3), then i1, j1 (ordinal 5).
 */
@Mixin(value = BlockPistonBase.class, priority = 1100)
public abstract class MixinBlockPistonBase_Deep {

    @Definition(id = "j1", local = @Local(type = int.class, ordinal = 5))
    @Expression("j1 <= 0")
    @ModifyExpressionValue(method = "canExtend", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private static boolean bellow$floorCanExtend(boolean original, @Local(argsOnly = true) World world,
        @Local(type = int.class, ordinal = 5) int j1) {
        return j1 <= BellowAPI.getMinY(world);
    }

    @Definition(id = "j1", local = @Local(type = int.class, ordinal = 5))
    @Expression("j1 <= 0")
    @ModifyExpressionValue(method = "tryExtend", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$floorTryExtend(boolean original, @Local(argsOnly = true) World world,
        @Local(type = int.class, ordinal = 5) int j1) {
        return j1 <= BellowAPI.getMinY(world);
    }
}
