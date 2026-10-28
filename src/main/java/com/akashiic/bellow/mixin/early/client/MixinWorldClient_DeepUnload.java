package com.akashiic.bellow.mixin.early.client;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Unloading a chunk on the client marks Y 0..256 for a render update, so the renderers of its deep sections kept their
 * geometry: deep terrain stayed drawn under a chunk the server had unloaded. The range starts at the floor.
 */
@Mixin(value = WorldClient.class, priority = 1100)
public abstract class MixinWorldClient_DeepUnload {

    @ModifyArg(
        method = "doPreChunk(IIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;markBlockRangeForRenderUpdate(IIIIII)V"),
        index = 1,
        require = 1,
        allow = 1)
    private int bellow$fromFloor(int minY) {
        return Math.min(minY, BellowAPI.getMinY((WorldClient) (Object) this));
    }
}
