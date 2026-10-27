package com.akashiic.bellow.net;

import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.world.chunk.Chunk;

/**
 * Builds partial chunk packets that carry only deep sections.
 * <p>
 * ChunkAPI serializes the packet inside the {@link S21PacketChunkData} constructor on the calling thread, so the wanted
 * deep slots are handed over through a thread local for the duration of that call.
 */
public final class DeepNet {

    private static final ThreadLocal<int[]> PENDING = ThreadLocal.withInitial(() -> new int[1]);

    private DeepNet() {}

    /** Deep slots requested for the partial S21 being built on this thread (0 outside {@link #deepOnlyPacket}). */
    public static int pendingSlotMask() {
        return PENDING.get()[0];
    }

    /** A non-forced S21 with no vanilla sections and the given deep slots. */
    public static S21PacketChunkData deepOnlyPacket(Chunk chunk, int slotMask) {
        int[] holder = PENDING.get();
        holder[0] = slotMask;
        try {
            return new S21PacketChunkData(chunk, false, 0);
        } finally {
            holder[0] = 0;
        }
    }
}
