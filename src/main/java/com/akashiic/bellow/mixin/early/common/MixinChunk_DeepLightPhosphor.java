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
 * ArchaicFix (Phosphor) replaces {@code Chunk.getSavedLightValue} with {@code getCachedLightFor}, a method it merges
 * into {@link Chunk}. That method indexes {@code storageArrays[y >> 4]} directly; route negative indices to the deep
 * sections. Priority must stay above ArchaicFix (1000) so the merged method exists when this mixin is applied.
 * Selected only when ArchaicFix's Phosphor backport is enabled.
 */
@Mixin(value = Chunk.class, priority = 1100)
public abstract class MixinChunk_DeepLightPhosphor {

    @Shadow
    public World worldObj;

    @Redirect(
        method = "getCachedLightFor",
        remap = false,
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
            args = "array=get",
            ordinal = 0,
            remap = true),
        require = 1)
    private ExtendedBlockStorage bellow$read(ExtendedBlockStorage[] array, int index) {
        if (index >= 0) return array[index];
        if (index << 4 < BellowAPI.getMinY(this.worldObj)) return null;
        return BellowAPI.getDeepSection((Chunk) (Object) this, index);
    }
}
