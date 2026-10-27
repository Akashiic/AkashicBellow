package com.akashiic.bellow.world;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.BellowConfig;
import com.akashiic.bellow.api.DeepConstants;

/**
 * Resolves the floor of a world.
 * <p>
 * Server worlds: the first time an enabled dimension of a save is opened, the configured floor is written to
 * {@code <save>/akashicbellow.dat}. From then on the stored value wins over the config, so a world can never end up
 * with chunks generated for two different floors. Disabling a dimension in the config hides its deep layer without
 * deleting it (chunk data stays dormant in NBT).
 * <p>
 * Client worlds start at 0 and are updated by the server ({@code DimensionInfoMessage}).
 */
public final class DeepWorlds {

    private static final String FILE_NAME = "akashicbellow.dat";
    private static final Map<File, NBTTagCompound> LOCKS = new HashMap<>();

    private DeepWorlds() {}

    public static synchronized int resolve(World world) {
        if (world.provider == null) return 0;
        // Client worlds: floor announced by the server (0 until then; the chunk packets also carry it).
        if (!(world instanceof WorldServer)) {
            Integer announced = world.isRemote ? ClientFloors.get(world.provider.dimensionId) : null;
            return announced == null ? 0 : announced;
        }
        int dim = world.provider.dimensionId;
        if (!BellowConfig.isDimensionEnabled(dim)) return 0;
        int configured = DeepConstants.sanitizeMinY(BellowConfig.minY);
        if (configured >= 0) return 0;

        ISaveHandler handler = world.getSaveHandler();
        File dir = handler == null ? null : handler.getWorldDirectory();
        if (dir == null) {
            AkashicBellow.LOG
                .warn("Dimension {} has no save directory; using configured floor {} without locking", dim, configured);
            return configured;
        }

        NBTTagCompound lock = LOCKS.computeIfAbsent(dir.getAbsoluteFile(), DeepWorlds::readLock);
        NBTTagCompound floors = lock.getCompoundTag("Floors");
        String key = Integer.toString(dim);
        if (floors.hasKey(key)) {
            int locked = DeepConstants.sanitizeMinY(floors.getInteger(key));
            if (locked != configured) {
                AkashicBellow.LOG.warn(
                    "Dimension {} of '{}' is locked to minY {}; ignoring configured {} (delete {} only together with the deep chunk data)",
                    dim,
                    dir.getName(),
                    locked,
                    configured,
                    FILE_NAME);
            }
            return locked;
        }

        floors.setInteger(key, configured);
        lock.setTag("Floors", floors);
        writeLock(dir, lock);
        AkashicBellow.LOG.info("Dimension {} of '{}' locked to minY {}", dim, dir.getName(), configured);
        return configured;
    }

    /** Forget cached lock files, e.g. when the integrated server stops and another save is opened. */
    public static synchronized void clearCache() {
        LOCKS.clear();
    }

    private static NBTTagCompound readLock(File dir) {
        File file = new File(dir, FILE_NAME);
        if (!file.isFile()) return new NBTTagCompound();
        try (FileInputStream in = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        } catch (IOException e) {
            // A corrupt lock must not silently change the floor of an existing world.
            throw new IllegalStateException(
                "Cannot read " + file + "; refusing to guess the deep floor of this world",
                e);
        }
    }

    private static void writeLock(File dir, NBTTagCompound lock) {
        File file = new File(dir, FILE_NAME);
        File tmp = new File(dir, FILE_NAME + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                CompressedStreamTools.writeCompressed(lock, out);
            }
            if (file.exists() && !file.delete()) throw new IOException("cannot replace " + file);
            if (!tmp.renameTo(file)) throw new IOException("cannot rename " + tmp);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + file, e);
        }
    }
}
