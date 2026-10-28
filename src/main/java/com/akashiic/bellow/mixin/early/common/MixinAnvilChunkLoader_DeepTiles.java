package com.akashiic.bellow.mixin.early.common;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.akashiic.bellow.storage.DeepTiles;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Forge's {@code loadEntities} (main thread, both the synchronous and the async chunk IO paths) creates the tile
 * entities and entities of a chunk read from disk. It reads a copy in which the deep tile entities are merged back and
 * what must stay dormant is left out (see {@link DeepTiles}); the compound itself may be a save still queued for the
 * file IO thread, so it is not modified.
 */
@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader_DeepTiles {

    @ModifyVariable(
        method = "loadEntities(Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/chunk/Chunk;)V",
        at = @At("HEAD"),
        argsOnly = true,
        remap = false,
        require = 1)
    private NBTTagCompound bellow$restoreDeepTiles(NBTTagCompound level, @Local(argsOnly = true) Chunk chunk) {
        return DeepTiles.restore(level, chunk);
    }
}
