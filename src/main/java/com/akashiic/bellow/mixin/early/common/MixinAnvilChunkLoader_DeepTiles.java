package com.akashiic.bellow.mixin.early.common;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.storage.DeepTiles;

/**
 * Forge's {@code loadEntities} (main thread, both the synchronous and the async chunk IO paths) creates the tile
 * entities from {@code Level.TileEntities}; the deep ones are merged back into that list first (see
 * {@link DeepTiles}).
 */
@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader_DeepTiles {

    @Inject(
        method = "loadEntities(Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/chunk/Chunk;)V",
        at = @At("HEAD"),
        remap = false,
        require = 1)
    private void bellow$restoreDeepTiles(World world, NBTTagCompound level, Chunk chunk, CallbackInfo ci) {
        DeepTiles.restore(level, chunk);
    }
}
