package com.akashiic.bellow.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** {@code (j < 1 || j > 256)} drops a falling block that left the world; j is the second int local (floor(posY)). */
@Mixin(value = EntityFallingBlock.class, priority = 1100)
public abstract class MixinEntityFallingBlock_Deep {

    @Definition(id = "j", local = @Local(type = int.class, ordinal = 1))
    @Expression("j < 1")
    @ModifyExpressionValue(method = "onUpdate()V", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$belowWorld(boolean original, @Local(type = int.class, ordinal = 1) int j) {
        return j < BellowAPI.getMinY(((Entity) (Object) this).worldObj) + 1;
    }
}
