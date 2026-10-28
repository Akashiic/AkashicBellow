package com.akashiic.bellow.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityEnderman;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** Teleports look for ground {@code while (j > 0)}; endermen in the deep layer could not teleport at all. */
@Mixin(EntityEnderman.class)
public abstract class MixinEntityEnderman_Deep {

    @Definition(id = "j", local = @Local(type = int.class, ordinal = 1))
    @Expression("j > 0")
    @ModifyExpressionValue(method = "teleportTo(DDD)Z", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$groundAboveFloor(boolean original, @Local(type = int.class, ordinal = 1) int j) {
        return j > BellowAPI.getMinY(((Entity) (Object) this).worldObj);
    }
}
