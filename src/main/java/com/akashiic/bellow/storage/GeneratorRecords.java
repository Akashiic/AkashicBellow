package com.akashiic.bellow.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.api.DeepConstants;

/**
 * What each deep generator did to a chunk, saved with its deep data ({@code akashicbellow.deep.Gens}):
 *
 * <pre>
 *   Gens: { "&lt;generator id&gt;": { S: stages done (byte), F: stages that threw (byte), E: callback failures (byte),
 *                                V: generator version when decorated (string), Data: compound owned by the generator } }
 * </pre>
 *
 * Records of generators that are not installed in this session are kept and saved unchanged, so removing an addon for a
 * while loses nothing. Main thread only, except while the chunk is read or cloned (it is not in the world yet).
 */
public final class GeneratorRecords {

    public static final int TERRAIN = DeepConstants.STAGE_TERRAIN;
    public static final int DECORATE = DeepConstants.STAGE_DECORATE;
    public static final int ON_DECORATED = DeepConstants.STAGE_ON_DECORATED;
    public static final int ON_LIT = DeepConstants.STAGE_ON_LIT;

    /** Failed {@code onDecorated}/{@code onLit} calls of one generator on one chunk before Bellow stops retrying. */
    public static final int MAX_CALLBACK_FAILURES = 3;

    private GeneratorRecords() {}

    private static NBTTagCompound record(Chunk chunk, String id, boolean create) {
        if (!(chunk instanceof IDeepChunkInternal)) return null; // unit tests run without the mixins
        IDeepChunkInternal deep = (IDeepChunkInternal) chunk;
        NBTTagCompound all = deep.bellow$getGeneratorRecords();
        if (all == null) {
            if (!create) return null;
            deep.bellow$setGeneratorRecords(all = new NBTTagCompound());
        }
        if (all.hasKey(id, 10)) return all.getCompoundTag(id);
        if (!create) return null;
        NBTTagCompound r = new NBTTagCompound();
        all.setTag(id, r);
        return r;
    }

    /** Bits of the stages that ran for this generator on this chunk (0 when it never saw the chunk). */
    public static int stages(Chunk chunk, String id) {
        NBTTagCompound r = record(chunk, id, false);
        return r == null ? 0 : r.getByte("S") & 0xFF;
    }

    public static int failed(Chunk chunk, String id) {
        NBTTagCompound r = record(chunk, id, false);
        return r == null ? 0 : r.getByte("F") & 0xFF;
    }

    public static void done(Chunk chunk, String id, int stage) {
        NBTTagCompound r = record(chunk, id, true);
        if (r == null) return;
        r.setByte("S", (byte) (r.getByte("S") | stage));
        chunk.setChunkModified();
    }

    /** A stage that threw. Terrain and decoration count as done (their partial writes stay); callbacks are retried. */
    public static void failedAt(Chunk chunk, String id, int stage) {
        NBTTagCompound r = record(chunk, id, true);
        if (r == null) return;
        r.setByte("F", (byte) (r.getByte("F") | stage));
        if (stage == ON_DECORATED || stage == ON_LIT) {
            int failures = (r.getByte("E") & 0xFF) + 1;
            r.setByte("E", (byte) Math.min(failures, 255));
            if (failures >= MAX_CALLBACK_FAILURES) r.setByte("S", (byte) (r.getByte("S") | stage));
        } else {
            r.setByte("S", (byte) (r.getByte("S") | stage));
        }
        chunk.setChunkModified();
    }

    public static void setVersion(Chunk chunk, String id, String version) {
        NBTTagCompound r = version == null || version.isEmpty() ? null : record(chunk, id, true);
        if (r != null) r.setString("V", version);
    }

    public static String version(Chunk chunk, String id) {
        NBTTagCompound r = record(chunk, id, false);
        return r == null ? "" : r.getString("V");
    }

    /** The generator's own data for this chunk, created on first use. */
    public static NBTTagCompound data(Chunk chunk, String id) {
        NBTTagCompound r = record(chunk, id, true);
        if (r == null) return new NBTTagCompound();
        if (!r.hasKey("Data", 10)) r.setTag("Data", new NBTTagCompound());
        return r.getCompoundTag("Data");
    }
}
