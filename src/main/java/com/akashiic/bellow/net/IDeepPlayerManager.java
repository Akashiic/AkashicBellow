package com.akashiic.bellow.net;

/**
 * Implemented by {@code PlayerManager} through a mixin. The update queue is a {@code List} on Forge and a
 * {@code Queue} on Crucible (CraftBukkit), so it is reached through this method instead of an accessor.
 */
public interface IDeepPlayerManager {

    /** Schedules a {@code PlayerInstance} for {@code sendChunkUpdate} at the end of this tick. */
    void bellow$queueForUpdate(Object playerInstance);
}
