package com.akashiic.bellow.mixin.early.client;

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

/**
 * Light lookups of the vanilla chunk mesher. Both methods start with
 * {@code if (y < 0) y = 0; if (y >= 256) y = 255; if (y >= 0 && y < 256 && ...)}.
 */
@Mixin(value = ChunkCache.class, priority = 1100)
public abstract class MixinChunkCache_DeepClient {

    @Shadow
    protected World worldObj;

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = { "getSkyBlockTypeBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I",
            "getSpecialBlockBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I" },
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 2,
        allow = 2)
    private boolean bellow$below(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < BellowAPI.getMinY(this.worldObj);
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y = @(0)")
    @ModifyExpressionValue(
        method = { "getSkyBlockTypeBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I",
            "getSpecialBlockBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I" },
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 2,
        allow = 2)
    private int bellow$clamp(int zero) {
        return BellowAPI.getMinY(this.worldObj);
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(
        method = { "getSkyBlockTypeBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I",
            "getSpecialBlockBrightness(Lnet/minecraft/world/EnumSkyBlock;III)I" },
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 2,
        allow = 2)
    private boolean bellow$atLeast(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= BellowAPI.getMinY(this.worldObj);
    }
}
