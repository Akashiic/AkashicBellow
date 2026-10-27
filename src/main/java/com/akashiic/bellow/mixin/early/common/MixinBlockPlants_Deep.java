package com.akashiic.bellow.mixin.early.common;

import net.minecraft.block.BlockLilyPad;
import net.minecraft.block.BlockMushroom;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** Mushrooms and lily pads pop off below Y 0 in vanilla ({@code y >= 0 && y < 256}). */
@Mixin(value = { BlockMushroom.class, BlockLilyPad.class }, priority = 1100)
public abstract class MixinBlockPlants_Deep {

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(
        method = "canBlockStay(Lnet/minecraft/world/World;III)Z",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$stay(boolean original, @Local(argsOnly = true) World world,
        @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= BellowAPI.getMinY(world);
    }
}
