package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.gen.DeepGen;

/** Generates the deep layer before {@code ChunkEvent.Load} is posted, so load listeners already see it. */
@Mixin(value = Chunk.class, priority = 1100)
public abstract class MixinChunk_DeepLoad {

    @Inject(method = "onChunkLoad()V", at = @At("HEAD"), require = 1)
    private void bellow$generateDeep(CallbackInfo ci) {
        DeepGen.onChunkLoad((Chunk) (Object) this);
    }
}
