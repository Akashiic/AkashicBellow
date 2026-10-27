package com.akashiic.bellow.mixin.early.common;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Tile entities below Y 0. Hodgepodge validates in-chunk coordinates at the head of this method and throws for
 * {@code y < 0} unless CubicChunks is installed. HEAD callbacks run in application order, so this mixin uses a lower
 * priority than Hodgepodge (applied first, runs first). It runs the vanilla body for deep coordinates inside the floor
 * and returns; every other case falls through unchanged.
 */
@Mixin(value = Chunk.class, priority = 900)
public abstract class MixinChunk_DeepTileEntity {

    @Shadow
    public World worldObj;

    @Shadow
    @Final
    public int xPosition;

    @Shadow
    @Final
    public int zPosition;

    @Shadow
    public Map<ChunkPosition, TileEntity> chunkTileEntityMap;

    @Shadow
    public abstract Block getBlock(int x, int y, int z);

    @Shadow
    public abstract int getBlockMetadata(int x, int y, int z);

    @Inject(
        method = "func_150812_a(IIILnet/minecraft/tileentity/TileEntity;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void bellow$deepTileEntity(int x, int y, int z, TileEntity te, CallbackInfo ci) {
        if (y >= 0 || y < BellowAPI.getMinY(this.worldObj) || (x & ~15) != 0 || (z & ~15) != 0) return;
        ci.cancel();
        ChunkPosition pos = new ChunkPosition(x, y, z);
        te.setWorldObj(this.worldObj);
        te.xCoord = this.xPosition * 16 + x;
        te.yCoord = y;
        te.zCoord = this.zPosition * 16 + z;
        int meta = this.getBlockMetadata(x, y, z);
        if (this.getBlock(x, y, z)
            .hasTileEntity(meta)) {
            TileEntity old = this.chunkTileEntityMap.get(pos);
            if (old != null) old.invalidate();
            te.validate();
            this.chunkTileEntityMap.put(pos, te);
        }
    }
}
