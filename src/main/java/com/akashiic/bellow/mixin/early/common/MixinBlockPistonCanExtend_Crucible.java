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
 * {@code j1 <= 0} on Crucible, where the body of {@code canExtend} moved to {@code canExtend_IntCB} (CraftBukkit
 * returns the push length for its piston events) and {@code canExtend} only delegates to it.
 */
@Mixin(value = BlockPistonBase.class, priority = 1100)
public abstract class MixinBlockPistonCanExtend_Crucible {

    @Definition(id = "j1", local = @Local(type = int.class, ordinal = 5))
    @Expression("j1 <= 0")
    @ModifyExpressionValue(
        method = "canExtend_IntCB",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        remap = false,
        require = 1,
        allow = 1)
    private static boolean bellow$floorCanExtend(boolean original, @Local(argsOnly = true) World world,
        @Local(type = int.class, ordinal = 5) int j1) {
        return j1 <= BellowAPI.getMinY(world);
    }
}
