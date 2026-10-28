package com.akashiic.bellow.mixin.early.crucible;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Crucible only. CraftBukkit adds {@code j > 0 && j < 256} to the landing test of a falling block; below Y 0 the block
 * never lands and is dropped as an item instead. j is the second int local (floor(posY)), as in the vanilla check.
 */
@Mixin(value = EntityFallingBlock.class, priority = 1100)
public abstract class MixinEntityFallingBlock_DeepCrucible {

    @Definition(id = "j", local = @Local(type = int.class, ordinal = 1))
    @Expression("j > 0")
    @ModifyExpressionValue(method = "onUpdate()V", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$landsAboveFloor(boolean original, @Local(type = int.class, ordinal = 1) int j) {
        return j > BellowAPI.getMinY(((Entity) (Object) this).worldObj);
    }
}
