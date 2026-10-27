package com.akashiic.bellow.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/** Entities die 64 blocks below the floor of the world instead of 64 blocks below Y 0. */
@Mixin(value = Entity.class, priority = 1100)
public abstract class MixinEntity_DeepVoid {

    @Shadow
    public World worldObj;

    @ModifyConstant(method = "onEntityUpdate()V", constant = @Constant(doubleValue = -64.0D), require = 1, allow = 1)
    private double bellow$voidLevel(double original) {
        return original + BellowAPI.getMinY(this.worldObj);
    }
}
