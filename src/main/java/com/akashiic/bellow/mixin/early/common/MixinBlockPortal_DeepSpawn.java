package com.akashiic.bellow.mixin.early.common;

import net.minecraft.block.BlockPortal;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Overworld portals spawn zombie pigmen on the ground found {@code while (l > 0)}, and only {@code if (l > 0)}: a
 * portal below Y 0 never spawned one. Both tests use the floor.
 */
@Mixin(BlockPortal.class)
public abstract class MixinBlockPortal_DeepSpawn {

    @Definition(id = "l", local = @Local(type = int.class, ordinal = 3))
    @Expression("l > 0")
    @ModifyExpressionValue(
        method = "updateTick(Lnet/minecraft/world/World;IIILjava/util/Random;)V",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 2,
        allow = 2)
    private boolean bellow$groundAboveFloor(boolean original, @Local(argsOnly = true) World world,
        @Local(type = int.class, ordinal = 3) int l) {
        return l > BellowAPI.getMinY(world);
    }
}
