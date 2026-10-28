package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidFinite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** Forge finite fluids delete themselves when they would flow to {@code otherY < 0} (every tick below Y 0). */
@Mixin(value = BlockFluidFinite.class, remap = false)
public abstract class MixinBlockFluidFinite_Deep {

    @Definition(id = "otherY", local = @Local(type = int.class, ordinal = 4))
    @Expression("otherY < 0")
    @ModifyExpressionValue(
        method = "tryToFlowVerticallyInto(Lnet/minecraft/world/World;IIII)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$outsideFloor(boolean original, @Local(argsOnly = true) World world,
        @Local(type = int.class, ordinal = 4) int otherY) {
        return otherY < BellowAPI.getMinY(world);
    }
}
