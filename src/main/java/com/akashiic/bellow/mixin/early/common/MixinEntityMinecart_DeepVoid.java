package com.akashiic.bellow.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/** Minecarts have their own copy of the void check. */
@Mixin(value = EntityMinecart.class, priority = 1100)
public abstract class MixinEntityMinecart_DeepVoid {

    @ModifyConstant(method = "onUpdate()V", constant = @Constant(doubleValue = -64.0D), require = 1, allow = 1)
    private double bellow$voidLevel(double original) {
        return original + BellowAPI.getMinY(((Entity) (Object) this).worldObj);
    }
}
