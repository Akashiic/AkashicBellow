package com.akashiic.bellow.mixin.late.buildcraft;

import java.util.BitSet;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashiic.bellow.api.BellowAPI;

/**
 * BuildCraft's per-chunk property cache (soft, ore, wood... for robots, builders, fillers and quarries) keeps one bit
 * per block at {@code x * height * 16 + y * 16 + z}, so any Y below 0 is a negative {@code BitSet} index: breaking a
 * block below 0 in a chunk the cache covers threw out of {@code World.markBlockForUpdate} (the player was kicked with
 * "Internal server error"). The cache now covers {@code minY..height-1}; positions outside it (the client before the
 * server announced its floor) read false and are not stored. With a floor of 0 the layout is BuildCraft's own.
 */
@Pseudo
@Mixin(targets = "buildcraft.core.properties.ChunkProperty", remap = false)
public abstract class MixinChunkProperty_Deep {

    @Shadow
    @Final
    private BitSet property;

    @Shadow
    @Final
    private int worldHeight;

    @Shadow
    @Final
    private IBlockAccess blockAccess;

    /** The floor of the world when this cache was made (the bit layout depends on it). */
    @Unique
    private int bellow$minY;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void bellow$floor(IBlockAccess access, int height, int x, int z, CallbackInfo ci) {
        this.bellow$minY = access instanceof World ? BellowAPI.getMinY((World) access) : 0;
    }

    @Unique
    private int bellow$index(int xChunk, int y, int zChunk) {
        if (y < this.bellow$minY || y >= this.worldHeight) return -1;
        return (xChunk * (this.worldHeight - this.bellow$minY) + (y - this.bellow$minY)) * 16 + zChunk;
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$get(int xChunk, int y, int zChunk, CallbackInfoReturnable<Boolean> cir) {
        int i = this.bellow$index(xChunk, y, zChunk);
        cir.setReturnValue(i >= 0 && this.property.get(i));
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$set(int xChunk, int y, int zChunk, boolean value, CallbackInfo ci) {
        int i = this.bellow$index(xChunk, y, zChunk);
        if (i >= 0) this.property.set(i, value);
        ci.cancel();
    }
}
