package com.akashiic.bellow.net;

import java.util.Arrays;
import java.util.BitSet;

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

    /** Past this many changes in one tick, duplicates are found with a bit set instead of a linear scan. */
    private static final int LINEAR_LIMIT = 128;

    private int[] positions = new int[8];
    private int count;
    private int slotMask;
    /** Index {@code (y + 512) << 8 | x << 4 | z}; only while a large edit is pending (16 KB). */
    private BitSet seen;

    public boolean isEmpty() {
        return this.count == 0;
    }

    public void add(int localX, int y, int localZ) {
        int packed = y << 8 | (localX & 15) << 4 | localZ & 15;
        if (this.seen != null) {
            if (!mark(this.seen, packed)) return;
        } else {
            for (int i = 0; i < this.count; i++) {
                if (this.positions[i] == packed) return;
            }
            if (this.count == LINEAR_LIMIT) {
                this.seen = new BitSet(-DeepConstants.ABSOLUTE_MIN_Y << 8);
                for (int i = 0; i < this.count; i++) mark(this.seen, this.positions[i]);
                mark(this.seen, packed);
            }
        }
        if (this.count == this.positions.length) this.positions = Arrays.copyOf(this.positions, this.count << 1);
        this.positions[this.count++] = packed;
        this.slotMask |= 1 << DeepConstants.slot(y >> 4);
    }

    public void clear() {
        this.count = 0;
        this.slotMask = 0;
        this.seen = null;
    }

    /** Sets the bit of a packed position; false if it was already set. */
    private static boolean mark(BitSet set, int packed) {
        int index = ((packed >> 8) - DeepConstants.ABSOLUTE_MIN_Y) << 8 | (packed & 0xFF);
        if (set.get(index)) return false;
        set.set(index);
        return true;
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
        this.clear();
    }
}
