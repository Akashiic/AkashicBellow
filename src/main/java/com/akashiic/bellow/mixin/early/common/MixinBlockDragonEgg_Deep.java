package com.akashiic.bellow.mixin.early.common;

import net.minecraft.block.BlockDragonEgg;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** The dragon egg falls only {@code if (y >= 0)} and lands {@code while (y > 0)}: below Y 0 it floated. */
@Mixin(BlockDragonEgg.class)
public abstract class MixinBlockDragonEgg_Deep {

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(method = "func_150018_e", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$fallsAboveFloor(boolean original, @Local(argsOnly = true) World world,
        @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= BellowAPI.getMinY(world);
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y > 0")
    @ModifyExpressionValue(method = "func_150018_e", at = @At("MIXINEXTRAS:EXPRESSION"), require = 2, allow = 2)
    private boolean bellow$landsAboveFloor(boolean original, @Local(argsOnly = true) World world,
        @Local(argsOnly = true, ordinal = 1) int y) {
        return y > BellowAPI.getMinY(world);
    }
}
