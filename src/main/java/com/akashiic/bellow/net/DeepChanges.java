package com.akashiic.bellow.net;

import java.util.Arrays;

import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.api.DeepConstants;

/**
 * Block changes below Y 0 of one watched chunk, collected between two player-manager ticks.
 * <p>
 * Vanilla packs a change as {@code x << 12 | z << 8 | y} in a short, which cannot represent negative Y, so deep changes
 * are kept apart: {@code (y << 8) | (x << 4) | z} in an int.
 */
public final class DeepChanges {

    public interface Sink {

        void bellow$send(Packet packet);
    }

    private int[] positions = new int[8];
    private int count;
    private int slotMask;

    public boolean isEmpty() {
        return this.count == 0;
    }

    public void add(int localX, int y, int localZ) {
        int packed = y << 8 | (localX & 15) << 4 | localZ & 15;
        for (int i = 0; i < this.count; i++) {
            if (this.positions[i] == packed) return;
        }
        if (this.count == this.positions.length) this.positions = Arrays.copyOf(this.positions, this.count << 1);
        this.positions[this.count++] = packed;
        this.slotMask |= 1 << DeepConstants.slot(y >> 4);
    }

    /** Sends the collected changes and resets. */
    public void flush(WorldServer world, int chunkX, int chunkZ, int clumpingThreshold, Sink sink) {
        if (this.count == 0) return;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        if (this.count < clumpingThreshold) {
            for (int i = 0; i < this.count; i++) {
                int p = this.positions[i];
                sink.bellow$send(new S23PacketBlockChange(baseX + (p >> 4 & 15), p >> 8, baseZ + (p & 15), world));
            }
        } else {
            Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
            sink.bellow$send(DeepNet.deepOnlyPacket(chunk, this.slotMask));
        }
        for (int i = 0; i < this.count; i++) {
            int p = this.positions[i];
            int x = baseX + (p >> 4 & 15);
            int y = p >> 8;
            int z = baseZ + (p & 15);
            if (world.getBlock(x, y, z)
                .hasTileEntity(world.getBlockMetadata(x, y, z))) {
                TileEntity te = world.getTileEntity(x, y, z);
                Packet description = te == null ? null : te.getDescriptionPacket();
                if (description != null) sink.bellow$send(description);
            }
        }
        this.count = 0;
        this.slotMask = 0;
    }
}
