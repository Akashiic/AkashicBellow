package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;

import com.akashiic.bellow.world.LegacyGeneration;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Delimits the legacy population phase; see {@link LegacyGeneration}. Nested population (cascades) is counted. */
@Mixin(value = Chunk.class, priority = 1100)
public abstract class MixinChunk_LegacyPopulate {

    @WrapMethod(
        method = "populateChunk(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;II)V")
    private void bellow$legacyPhase(IChunkProvider provider, IChunkProvider generator, int x, int z,
        Operation<Void> original) {
        LegacyGeneration.enter();
        try {
            original.call(provider, generator, x, z);
        } finally {
            LegacyGeneration.exit();
        }
    }
}
