package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.gen.DeepGen;

/**
 * Deep terrain before {@code ChunkEvent.Load} is posted (load listeners already see it); decoration once the chunk's
 * tile entities and entities are registered with the world (see {@link DeepGen#afterChunkLoad}).
 */
@Mixin(value = Chunk.class, priority = 1100)
public abstract class MixinChunk_DeepLoad {

    @Inject(method = "onChunkLoad()V", at = @At("HEAD"), require = 1)
    private void bellow$generateDeep(CallbackInfo ci) {
        DeepGen.onChunkLoad((Chunk) (Object) this);
    }

    @Inject(method = "onChunkLoad()V", at = @At("RETURN"), require = 1)
    private void bellow$decorateDeep(CallbackInfo ci) {
        DeepGen.afterChunkLoad((Chunk) (Object) this);
    }
}
