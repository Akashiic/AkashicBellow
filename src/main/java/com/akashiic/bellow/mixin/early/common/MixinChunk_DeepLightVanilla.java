package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Deep section lookup for the vanilla {@code Chunk.getSavedLightValue}. Not applied when ArchaicFix's Phosphor
 * overwrites that method (see {@code archaic.MixinChunk_DeepLightPhosphor}).
 * <p>
 * A missing deep section reports 0: unlike missing sections above the terrain, there is never sky above it.
 */
@Mixin(value = Chunk.class, priority = 1100)
public abstract class MixinChunk_DeepLightVanilla {

    @Shadow
    public World worldObj;

    @Redirect(
        method = "getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get",
            ordinal = 0),
        require = 1)
    private ExtendedBlockStorage bellow$read(ExtendedBlockStorage[] array, int index) {
        if (index >= 0) return array[index];
        if (index << 4 < BellowAPI.getMinY(this.worldObj)) return null;
        return BellowAPI.getDeepSection((Chunk) (Object) this, index);
    }
}
