package com.akashiic.bellow.mixin.late.angelica;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.compat.angelica.AngelicaFloor;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * {@code WorldSlice.prepare} reads {@code chunk.getBlockStorageArray()[origin.y]} to skip empty sections. For a deep
 * origin the array is replaced by a one-element array holding the deep section and the index by 0. The light helpers
 * of the slice start their valid range at the floor.
 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice", remap = false)
public abstract class MixinWorldSlice_Deep {

    @Definition(
        id = "getBlockStorageArray",
        method = "Lnet/minecraft/world/chunk/Chunk;getBlockStorageArray()[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
        remap = true)
    @Definition(id = "y", field = "Lcom/gtnewhorizons/angelica/compat/mojang/ChunkSectionPos;y:I")
    @Expression("@(?.getBlockStorageArray())[?.y]")
    @ModifyExpressionValue(method = "prepare", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private static ExtendedBlockStorage[] bellow$deepArray(ExtendedBlockStorage[] array,
        @Local(argsOnly = true) World world, @Local(argsOnly = true) ChunkSectionPos origin, @Local Chunk chunk) {
        if (origin.y >= 0) return array;
        ExtendedBlockStorage deep = origin.y << 4 < BellowAPI.getMinY(world) ? null
            : BellowAPI.getDeepSection(chunk, origin.y);
        return new ExtendedBlockStorage[] { deep };
    }

    @Definition(
        id = "getBlockStorageArray",
        method = "Lnet/minecraft/world/chunk/Chunk;getBlockStorageArray()[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
        remap = true)
    @Definition(id = "y", field = "Lcom/gtnewhorizons/angelica/compat/mojang/ChunkSectionPos;y:I")
    @Expression("?.getBlockStorageArray()[@(?.y)]")
    @ModifyExpressionValue(method = "prepare", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private static int bellow$deepIndex(int index) {
        return index < 0 ? 0 : index;
    }

    // int minHeight = cubic ? CubicChunksAPI.getMinHeight(world) : 0; (first int local after the parameters)
    @ModifyVariable(
        method = { "getLightBrightnessForSkyBlocks", "func_72802_i" },
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 4,
        require = 1,
        allow = 1)
    private int bellow$brightnessFloor(int minHeight) {
        return Math.min(minHeight, AngelicaFloor.current());
    }

    @ModifyVariable(
        method = "getRawSkyLight",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 3,
        require = 1,
        allow = 1)
    private int bellow$skyFloor(int minHeight) {
        return Math.min(minHeight, AngelicaFloor.current());
    }
}
