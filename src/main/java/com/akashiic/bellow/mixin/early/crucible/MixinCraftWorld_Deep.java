package com.akashiic.bellow.mixin.early.crucible;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.sugar.Local;

/**
 * {@code CraftWorld.getBlockAt(x, y, z)} hands {@code y & 0xFF} to the chunk: Y -10 became Y 246, so every Bukkit
 * plugin and every CraftBukkit event built from it saw (and wrote) the block 256 above. Below Y 0 the mask becomes -1
 * ({@code y & -1 == y}); Y 0..255 and the masking above 255 are unchanged.
 */
@Pseudo
@Mixin(targets = "org.bukkit.craftbukkit.v1_7_R4.CraftWorld", remap = false)
public abstract class MixinCraftWorld_Deep {

    @ModifyConstant(
        method = "getBlockAt(III)Lorg/bukkit/block/Block;",
        constant = @Constant(intValue = 255),
        require = 1,
        allow = 1)
    private int bellow$keepNegativeY(int mask, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < 0 ? -1 : mask;
    }
}
