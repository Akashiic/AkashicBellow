package com.akashiic.bellow.mixin.early.common;

import net.minecraft.block.BlockFalling;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** Sand/gravel: start falling ({@code y >= 0}) and instant fall ({@code y > 0}, twice) honour the floor. */
@Mixin(value = BlockFalling.class, priority = 1100)
public abstract class MixinBlockFalling_Deep {

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(method = "func_149830_m", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$canFall(boolean original, @Local(argsOnly = true) World world,
        @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= BellowAPI.getMinY(world);
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y > 0")
    @ModifyExpressionValue(method = "func_149830_m", at = @At("MIXINEXTRAS:EXPRESSION"), require = 2, allow = 2)
    private boolean bellow$aboveFloor(boolean original, @Local(argsOnly = true) World world,
        @Local(argsOnly = true, ordinal = 1) int y) {
        return y > BellowAPI.getMinY(world);
    }
}
