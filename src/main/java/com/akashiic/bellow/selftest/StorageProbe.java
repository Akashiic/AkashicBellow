package com.akashiic.bellow.selftest;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.storage.DeepDataManager;
import com.akashiic.bellow.storage.DeepTiles;
import com.akashiic.bellow.storage.GeneratorRecords;
import com.akashiic.bellow.storage.IDeepChunkInternal;
import com.akashiic.bellow.storage.IDormantData;

/**
 * Storage edge cases that a normal world never shows, on detached chunks (never added to the world):
 * <ul>
 * <li>the compound handed to Forge's {@code loadEntities} is never modified (it can be a save still queued for the
 * file IO thread);</li>
 * <li>tile entities and entities below the floor (a lost floor lock, a moved world) stay dormant and are written back
 * on save instead of crashing the chunk load or vanishing;</li>
 * <li>a deep section that cannot be read loads as empty and is written back unchanged, without failing the chunk;</li>
 * <li>generator records and the light flag are saved, read and cloned as independent copies;</li>
 * <li>a chunk of a newer deep format (read-only) never gets a deep section;</li>
 * <li>a world saved by a newer deep format asks for confirmation through ChunkAPI.</li>
 * </ul>
 */
public final class StorageProbe {

    private StorageProbe() {}

    public static void run(WorldServer world, BukkitProbe.Check check) {
        int minY = BellowAPI.getMinY(world);
        int cx = 3000, cz = 3000;

        // --- restore does not modify its input
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList vanilla = new NBTTagList();
        vanilla.appendTag(tile("Chest", cx * 16 + 1, 64, cz * 16 + 1));
        level.setTag("TileEntities", vanilla);
        NBTTagList deep = new NBTTagList();
        deep.appendTag(tile("Chest", 5, minY + 3, 7)); // relative x/z, as a relocated chunk would have
        deep.appendTag(tile("Chest", cx * 16 + 2, minY - 16, cz * 16 + 2)); // below the floor
        level.setTag(DeepTiles.KEY, deep);
        NBTTagList entities = new NBTTagList();
        entities.appendTag(entity(cx * 16 + 3.5, 70.0, cz * 16 + 3.5));
        entities.appendTag(entity(cx * 16 + 4.5, minY - 10.0, cz * 16 + 4.5)); // below the floor
        level.setTag("Entities", entities);
        NBTTagCompound before = (NBTTagCompound) level.copy();

        Chunk chunk = new Chunk(world, cx, cz);
        NBTTagCompound read = DeepTiles.restore(level, chunk);
        check.check("restore leaves the loaded compound unchanged", level.equals(before), "");
        NBTTagList tiles = read.getTagList("TileEntities", 10);
        NBTTagCompound relocated = tiles.tagCount() == 2 ? tiles.getCompoundTagAt(1) : null;
        check.check(
            "restore merges deep tile entities into the copy",
            tiles.tagCount() == 2 && relocated.getInteger("x") == cx * 16 + 5
                && relocated.getInteger("z") == cz * 16 + 7
                && !read.hasKey(DeepTiles.KEY),
            tiles.tagCount() + " tile entities, deep one at " + (relocated == null ? "-" : relocated.getInteger("x")));

        // --- below the floor: dormant, not loaded, written back on save
        IDormantData dormant = (IDormantData) chunk;
        NBTTagList dTiles = dormant.bellow$getDormantTiles(), dEntities = dormant.bellow$getDormantEntities();
        check.check(
            "tile entities and entities below the floor stay dormant",
            dTiles != null && dTiles.tagCount() == 1
                && dEntities != null
                && dEntities.tagCount() == 1
                && read.getTagList("Entities", 10)
                    .tagCount() == 1,
            "dormant tiles " + (dTiles == null ? 0 : dTiles.tagCount())
                + ", dormant entities "
                + (dEntities == null ? 0 : dEntities.tagCount())
                + ", loaded entities "
                + read.getTagList("Entities", 10)
                    .tagCount());
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound saved = new NBTTagCompound();
        saved.setTag("TileEntities", new NBTTagList()); // the live chunk had no tile entities left
        root.setTag("Level", saved);
        DeepTiles.strip(chunk, root);
        NBTTagList savedDeep = saved.getTagList(DeepTiles.KEY, 10);
        NBTTagList savedEntities = saved.getTagList(DeepTiles.KEY_ENTITIES, 10);
        check.check(
            "dormant data is written back on save",
            savedDeep.tagCount() == 1 && savedDeep.getCompoundTagAt(0)
                .getInteger("y") == minY - 16 && savedEntities.tagCount() == 1,
            savedDeep.tagCount() + " tile entities, " + savedEntities.tagCount() + " entities");

        // --- a section that cannot be read
        DeepDataManager manager = new DeepDataManager();
        Chunk broken = new Chunk(world, cx + 1, cz);
        NBTTagCompound data = new NBTTagCompound();
        data.setByte("V", (byte) 1);
        data.setByte("Gen", DeepConstants.GEN_DECORATED);
        NBTTagList sections = new NBTTagList();
        NBTTagCompound bad = new NBTTagCompound();
        bad.setByte("Y", (byte) -2);
        // unreadable with and without EndlessIDs: vanilla reads Blocks (4096 bytes expected), EndlessIDs reads Blocks16
        // when present (at most 8192 bytes; more overruns its arrays)
        bad.setByteArray("Blocks", new byte[10]);
        bad.setByteArray("Data", new byte[10]);
        bad.setByteArray("Blocks16", new byte[9000]);
        sections.appendTag(bad);
        data.setTag("Sections", sections);
        String error = null;
        try {
            manager.readChunkFromNBT(broken, data);
        } catch (RuntimeException e) {
            error = e.toString();
        }
        int slot = DeepConstants.slot(-2);
        ExtendedBlockStorageView view = new ExtendedBlockStorageView((IDeepChunk) broken, slot);
        NBTTagCompound out = new NBTTagCompound();
        manager.writeChunkToNBT(broken, out);
        NBTTagList written = out.getTagList("Sections", 10);
        boolean kept = written.tagCount() == 1 && written.getCompoundTagAt(0)
            .getByteArray("Blocks16").length == 9000;
        check.check(
            "an unreadable deep section does not fail the chunk and is kept on disk",
            error == null && view.empty() && kept,
            "load error " + error + ", slot empty " + view.empty() + ", written back " + kept);

        // --- generator records and the light flag: written, read back, cloned, never shared with the saved compound
        Chunk gen = new Chunk(world, cx + 2, cz);
        IDeepChunkInternal genState = (IDeepChunkInternal) gen;
        genState.bellow$setGenState(DeepConstants.GEN_DECORATED);
        genState.bellow$setLightPending(true);
        GeneratorRecords.done(gen, "test:absent", GeneratorRecords.TERRAIN | GeneratorRecords.DECORATE);
        GeneratorRecords.data(gen, "test:absent")
            .setString("k", "v");
        NBTTagCompound saved1 = new NBTTagCompound();
        manager.writeChunkToNBT(gen, saved1);
        GeneratorRecords.done(gen, "test:absent", GeneratorRecords.ON_DECORATED); // after the save was written
        Chunk back = new Chunk(world, cx + 2, cz);
        manager.readChunkFromNBT(back, saved1);
        GeneratorRecords.done(back, "test:other", GeneratorRecords.TERRAIN); // must not reach saved1
        Chunk clone = new Chunk(world, cx + 2, cz);
        manager.cloneChunk(back, clone);
        GeneratorRecords.done(back, "test:third", GeneratorRecords.TERRAIN); // must not reach the clone
        boolean recWritten = saved1.getByte("L") == 1 && saved1.getCompoundTag("Gens")
            .getCompoundTag("test:absent")
            .getByte("S") == 3
            && !saved1.getCompoundTag("Gens")
                .hasKey("test:other");
        boolean recRead = ((IDeepChunkInternal) back).bellow$isLightPending()
            && GeneratorRecords.stages(back, "test:absent") == 3
            && "v".equals(
                GeneratorRecords.data(back, "test:absent")
                    .getString("k"));
        boolean recCloned = ((IDeepChunkInternal) clone).bellow$isLightPending()
            && GeneratorRecords.stages(clone, "test:other") == 1
            && GeneratorRecords.stages(clone, "test:third") == 0;
        check.check(
            "generator records and the light flag are saved, read and cloned as copies",
            recWritten && recRead && recCloned,
            "written " + recWritten + ", read " + recRead + ", cloned " + recCloned + " (" + saved1 + ")");

        // --- a chunk of a newer format never gets a deep section, not even for light
        // (and direct chunk writes there, or below the floor, are refused instead of indexing storageArrays[-n])
        Chunk frozen = new Chunk(world, cx + 3, cz);
        String thrown = null;
        boolean placed = true;
        DeepDataManager.markNewerForTests(frozen, true);
        try {
            frozen.setLightValue(net.minecraft.world.EnumSkyBlock.Block, 3, minY + 5, 3, 12);
            placed = frozen.func_150807_a(4, minY + 5, 4, net.minecraft.init.Blocks.stone, 0);
        } catch (RuntimeException e) {
            thrown = e.toString();
        } finally {
            DeepDataManager.markNewerForTests(frozen, false);
        }
        Chunk floor = new Chunk(world, cx + 4, cz);
        boolean placedBelow = true;
        try {
            floor.setLightValue(net.minecraft.world.EnumSkyBlock.Block, 3, minY - 3, 3, 12);
            placedBelow = floor.func_150807_a(4, minY - 3, 4, net.minecraft.init.Blocks.stone, 0);
        } catch (RuntimeException e) {
            thrown = e.toString();
        }
        check.check(
            "no deep section for a chunk of a newer format, no crash below the floor",
            ((IDeepChunk) frozen).bellow$peekDeepSections() == null && !placed && !placedBelow && thrown == null,
            "sections " + (((IDeepChunk) frozen).bellow$peekDeepSections() != null ? "created" : "none")
                + ", write accepted "
                + placed
                + " / below the floor "
                + placedBelow
                + ", exception "
                + thrown);

        // --- downgrade warning
        String newer = manager.versionChangeMessage("2");
        check.check(
            "a newer deep format asks for confirmation",
            newer != null && manager.versionChangeMessage("1") == null && "1".equals(manager.version()),
            "message for format 2: " + (newer != null));
    }

    private static NBTTagCompound tile(String id, int x, int y, int z) {
        NBTTagCompound te = new NBTTagCompound();
        te.setString("id", id);
        te.setInteger("x", x);
        te.setInteger("y", y);
        te.setInteger("z", z);
        return te;
    }

    private static NBTTagCompound entity(double x, double y, double z) {
        NBTTagCompound e = new NBTTagCompound();
        e.setString("id", "Pig");
        NBTTagList pos = new NBTTagList();
        pos.appendTag(new NBTTagDouble(x));
        pos.appendTag(new NBTTagDouble(y));
        pos.appendTag(new NBTTagDouble(z));
        e.setTag("Pos", pos);
        return e;
    }

    /** Whether a deep slot of a chunk holds no section. */
    private static final class ExtendedBlockStorageView {

        private final boolean empty;

        ExtendedBlockStorageView(IDeepChunk chunk, int slot) {
            net.minecraft.world.chunk.storage.ExtendedBlockStorage[] s = chunk.bellow$peekDeepSections();
            this.empty = s == null || s[slot] == null;
        }

        boolean empty() {
            return this.empty;
        }
    }
}
