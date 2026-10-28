package com.akashiic.bellow.storage;

import net.minecraft.nbt.NBTTagList;

/**
 * Implemented by {@link net.minecraft.world.chunk.Chunk} through a mixin: tile entities and entities read from disk
 * that this session must not load (below the floor the world resolves to now, or in deep data written by a newer
 * format). They are written back unchanged when the chunk is saved, like the dormant sections below the floor.
 */
public interface IDormantData {

    /** Null when there is none. */
    NBTTagList bellow$getDormantTiles();

    /** Null when there is none. */
    NBTTagList bellow$getDormantEntities();

    void bellow$setDormant(NBTTagList tiles, NBTTagList entities);
}
