package com.akashiic.bellow.mixin.late.galacticraft;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Galacticraft rockets (every {@code EntitySpaceshipBase}: tier 1-3, cargo) are killed server side when
 * {@code posY < 0.0}, which deletes a rocket standing in a deep base. The check is moved to the world floor.
 * {@code func_70071_h_} ({@code onUpdate}) holds exactly one {@code 0.0} constant: that comparison.
 */
@Pseudo
@Mixin(targets = "micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase", remap = false)
public abstract class MixinEntitySpaceshipBase_Deep {

    @ModifyConstant(method = "func_70071_h_", constant = @Constant(doubleValue = 0.0D, ordinal = 0), require = 1)
    private double bellow$killBelow(double zero) {
        return BellowAPI.getMinY(((Entity) (Object) this).worldObj);
    }
}
