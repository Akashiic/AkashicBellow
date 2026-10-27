package com.akashiic.bellow.mixin.early.crucible;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.sugar.Local;

/**
 * {@code CraftChunk.getBlock(x, y, z)} builds its CraftBlock with {@code y & 0xFF}; see {@link MixinCraftWorld_Deep}.
 */
@Pseudo
@Mixin(targets = "org.bukkit.craftbukkit.v1_7_R4.CraftChunk", remap = false)
public abstract class MixinCraftChunk_Deep {

    @ModifyConstant(
        method = "getBlock(III)Lorg/bukkit/block/Block;",
        constant = @Constant(intValue = 255),
        require = 1,
        allow = 1)
    private int bellow$keepNegativeY(int mask, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < 0 ? -1 : mask;
    }
}
