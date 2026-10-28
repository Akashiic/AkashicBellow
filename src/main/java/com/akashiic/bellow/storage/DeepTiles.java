package com.akashiic.bellow.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

/**
 * Keeps tile entities below Y 0 out of the vanilla {@code Level.TileEntities} list on disk, and keeps what this session
 * cannot load (below the current floor, or in deep data of a newer format) dormant instead of discarding it.
 * <p>
 * Vanilla (and ChunkAPI) write every tile entity of the chunk into that list. Loading one with {@code y < 0} without
 * the mod indexes {@code storageArrays[-1]} (or trips Hodgepodge's coordinate check) and crashes the chunk load, so a
 * world could not be opened after uninstalling. On save they move to {@code Level.AkashicBellowDeepTiles}, which only
 * this mod reads; on load they are merged back before Forge creates the tile entities. Chunks saved by older versions
 * (deep entries still in the vanilla list) load unchanged and are converted on their next save.
 * <p>
 * Tile entities and entities below the floor the world resolves to now (a lost floor lock, a world moved between
 * servers) would crash the chunk load (Hodgepodge's coordinate check) or vanish (no block to sit in, void below);
 * they are held on the chunk ({@link IDormantData}) and written back unchanged on save.
 */
public final class DeepTiles {

    public static final String KEY = "AkashicBellowDeepTiles";
    /** Entities held dormant (never loaded by anything else either). */
    public static final String KEY_ENTITIES = "AkashicBellowDormantEntities";

    private static boolean warnedDormant;

    private DeepTiles() {}

    /** On the main thread, after the chunk was written ({@code ChunkDataEvent.Save}), before it is queued for IO. */
    public static void strip(Chunk chunk, NBTTagCompound root) {
        NBTTagCompound level = root.getCompoundTag("Level");
        IDormantData dormant = (IDormantData) chunk;
        if (BellowAPI.hasDeepLayers(chunk.worldObj) && level.hasKey("TileEntities", 9)) {
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
            if (deep != null) {
                level.setTag("TileEntities", keep);
                level.setTag(KEY, deep);
            }
        }
        NBTTagList tiles = dormant.bellow$getDormantTiles();
        if (tiles != null) {
            NBTTagList deep = level.hasKey(KEY, 9) ? level.getTagList(KEY, 10) : new NBTTagList();
            for (int i = 0; i < tiles.tagCount(); i++) deep.appendTag(tiles.getCompoundTagAt(i));
            level.setTag(KEY, deep);
        }
        NBTTagList entities = dormant.bellow$getDormantEntities();
        if (entities != null) level.setTag(KEY_ENTITIES, entities.copy());
    }

    /**
     * On the main thread, before Forge reads {@code TileEntities} and {@code Entities}
     * ({@code AnvilChunkLoader.loadEntities}). Returns the compound Forge should read: the original when there is
     * nothing to change, else a copy. The original is never modified: when the chunk is loaded again while its save is
     * still queued, it is the very compound the file IO thread is writing.
     */
    public static NBTTagCompound restore(NBTTagCompound level, Chunk chunk) {
        // below this, nothing may load: the floor, or Y 0 for deep data this version cannot read
        int threshold = DeepDataManager.isNewer(chunk) ? 0 : BellowAPI.getMinY(chunk.worldObj);
        NBTTagList vanilla = level.getTagList("TileEntities", 10);
        NBTTagList entities = level.getTagList("Entities", 10);
        boolean deepKey = level.hasKey(KEY, 9), dormantKey = level.hasKey(KEY_ENTITIES, 9);
        if (!deepKey && !dormantKey && !anyTileBelow(vanilla, threshold) && !anyEntityBelow(entities, threshold)) {
            return level;
        }

        NBTTagCompound copy = new NBTTagCompound();
        for (Object key : level.func_150296_c()) {
            if (!KEY.equals(key) && !KEY_ENTITIES.equals(key)) copy.setTag((String) key, level.getTag((String) key));
        }
        NBTTagList tiles = new NBTTagList(), dormantTiles = new NBTTagList();
        for (int i = 0; i < vanilla.tagCount(); i++) {
            NBTTagCompound te = vanilla.getCompoundTagAt(i);
            (te.getInteger("y") < threshold ? dormantTiles : tiles).appendTag(te);
        }
        NBTTagList deep = level.getTagList(KEY, 10);
        for (int i = 0; i < deep.tagCount(); i++) {
            NBTTagCompound te = (NBTTagCompound) deep.getCompoundTagAt(i)
                .copy();
            if (te.getInteger("y") < threshold) {
                dormantTiles.appendTag(te);
                continue;
            }
            // a relocated chunk only fixes the entries of the vanilla list; keep these inside this chunk as well
            te.setInteger("x", chunk.xPosition * 16 + (te.getInteger("x") & 15));
            te.setInteger("z", chunk.zPosition * 16 + (te.getInteger("z") & 15));
            tiles.appendTag(te);
        }
        NBTTagList live = new NBTTagList(), dormantEntities = new NBTTagList();
        sortEntities(entities, threshold, live, dormantEntities);
        sortEntities(level.getTagList(KEY_ENTITIES, 10), threshold, live, dormantEntities);
        copy.setTag("TileEntities", tiles);
        copy.setTag("Entities", live);

        boolean any = dormantTiles.tagCount() > 0 || dormantEntities.tagCount() > 0;
        ((IDormantData) chunk).bellow$setDormant(
            dormantTiles.tagCount() > 0 ? dormantTiles : null,
            dormantEntities.tagCount() > 0 ? dormantEntities : null);
        if (any && !warnedDormant) {
            warnedDormant = true;
            AkashicBellow.LOG.warn(
                "Chunk [{}, {}] holds {} tile entities and {} entities below Y {} (saved under a deeper floor, or by a"
                    + " newer format); they are kept unchanged on disk but not loaded. Further chunks are not reported.",
                chunk.xPosition,
                chunk.zPosition,
                dormantTiles.tagCount(),
                dormantEntities.tagCount(),
                threshold);
        }
        return copy;
    }

    private static boolean anyTileBelow(NBTTagList tiles, int threshold) {
        for (int i = 0; i < tiles.tagCount(); i++) if (tiles.getCompoundTagAt(i)
            .getInteger("y") < threshold) return true;
        return false;
    }

    private static boolean anyEntityBelow(NBTTagList entities, int threshold) {
        for (int i = 0; i < entities.tagCount(); i++)
            if (entityY(entities.getCompoundTagAt(i)) < threshold) return true;
        return false;
    }

    private static void sortEntities(NBTTagList from, int threshold, NBTTagList live, NBTTagList dormant) {
        for (int i = 0; i < from.tagCount(); i++) {
            NBTTagCompound e = from.getCompoundTagAt(i);
            (entityY(e) < threshold ? dormant : live).appendTag(e);
        }
    }

    /** Feet Y of a saved entity; riders are saved inside their mount and follow it. */
    private static double entityY(NBTTagCompound entity) {
        NBTTagList pos = entity.getTagList("Pos", 6);
        return pos.tagCount() == 3 ? pos.func_150309_d(1) : 0.0;
    }
}
