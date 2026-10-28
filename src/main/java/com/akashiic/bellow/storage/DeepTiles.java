package com.akashiic.bellow.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Keeps tile entities below Y 0 out of the vanilla {@code Level.TileEntities} list on disk.
 * <p>
 * Vanilla (and ChunkAPI) write every tile entity of the chunk into that list. Loading one with {@code y < 0} without
 * the mod indexes {@code storageArrays[-1]} (or trips Hodgepodge's coordinate check) and crashes the chunk load, so a
 * world could not be opened after uninstalling. On save they move to {@code Level.AkashicBellowDeepTiles}, which only
 * this mod reads; on load they are merged back before Forge creates the tile entities. Chunks saved by older versions
 * (deep entries still in the vanilla list) load unchanged and are converted on their next save.
 */
public final class DeepTiles {

    public static final String KEY = "AkashicBellowDeepTiles";

    private DeepTiles() {}

    /** On the main thread, after the chunk was written ({@code ChunkDataEvent.Save}). */
    public static void strip(Chunk chunk, NBTTagCompound root) {
        if (!BellowAPI.hasDeepLayers(chunk.worldObj)) return;
        NBTTagCompound level = root.getCompoundTag("Level");
        if (!level.hasKey("TileEntities", 9)) return;
        NBTTagList all = level.getTagList("TileEntities", 10);
        NBTTagList keep = null, deep = null;
        for (int i = 0; i < all.tagCount(); i++) {
            NBTTagCompound te = all.getCompoundTagAt(i);
            if (te.getInteger("y") < 0) {
                if (deep == null) {
                    deep = new NBTTagList();
                    keep = new NBTTagList();
                    for (int j = 0; j < i; j++) keep.appendTag(all.getCompoundTagAt(j));
                }
                deep.appendTag(te);
            } else if (keep != null) {
                keep.appendTag(te);
            }
        }
        if (deep == null) return;
        level.setTag("TileEntities", keep);
        level.setTag(KEY, deep);
    }

    /** On the main thread, before Forge reads {@code TileEntities} ({@code AnvilChunkLoader.loadEntities}). */
    public static void restore(NBTTagCompound level, Chunk chunk) {
        if (!level.hasKey(KEY, 9)) return;
        NBTTagList deep = level.getTagList(KEY, 10);
        level.removeTag(KEY);
        NBTTagList all = level.getTagList("TileEntities", 10);
        if (!level.hasKey("TileEntities", 9)) level.setTag("TileEntities", all);
        for (int i = 0; i < deep.tagCount(); i++) {
            NBTTagCompound te = deep.getCompoundTagAt(i);
            // a relocated chunk only fixes the entries of the vanilla list; keep these inside this chunk as well
            te.setInteger("x", chunk.xPosition * 16 + (te.getInteger("x") & 15));
            te.setInteger("z", chunk.zPosition * 16 + (te.getInteger("z") & 15));
            all.appendTag(te);
        }
    }
}
