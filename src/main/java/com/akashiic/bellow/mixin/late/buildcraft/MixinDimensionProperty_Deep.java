package com.akashiic.bellow.mixin.late.buildcraft;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/**
 * BuildCraft fills a chunk's property cache with {@code for (y = 0; y < height; y++)}: the deep layer of a chunk the
 * cache covers would read "not soft, not ore" until each block changes. The loop starts at the floor
 * ({@link MixinChunkProperty_Deep} makes room for it). Verified against the 7.1.63 bytecode: {@code load} has three
 * {@code iconst_0}, the loop starts of x, y and z.
 */
@Pseudo
@Mixin(targets = "buildcraft.core.properties.DimensionProperty", remap = false)
public abstract class MixinDimensionProperty_Deep {

    @Shadow
    @Final
    private World world;

    @ModifyConstant(method = "load", constant = @Constant(intValue = 0, ordinal = 1), require = 1)
    private int bellow$fromFloor(int zero) {
        return BellowAPI.getMinY(this.world);
    }
}
