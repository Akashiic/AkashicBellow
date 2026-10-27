package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** {@link ChunkCache} backs path finding (server) and chunk meshing (client). */
@Mixin(value = ChunkCache.class, priority = 1100)
public abstract class MixinChunkCache_Deep {

    @Shadow
    protected World worldObj;

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(
        method = "getBlock(III)Lnet/minecraft/block/Block;",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$getBlock(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= BellowAPI.getMinY(this.worldObj);
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getBlockMetadata(III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$getMeta(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < BellowAPI.getMinY(this.worldObj);
    }
}
