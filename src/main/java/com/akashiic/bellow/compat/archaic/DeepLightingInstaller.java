package com.akashiic.bellow.compat.archaic;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.embeddedt.archaicfix.lighting.api.ILightingEngine;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

/**
 * Puts a {@link DeepPhosphorEngine} into the {@code lightingEngine} field that ArchaicFix merges into {@link World}.
 * Pending updates of the replaced engine are processed first (on the owning thread), and the per-chunk engine caches
 * that ArchaicFix keeps are cleared so that every chunk resolves the new engine.
 */
public final class DeepLightingInstaller {

    private static Field worldEngine;
    private static Field chunkEngine;

    private DeepLightingInstaller() {}

    public static synchronized void install(World world) {
        AkashicBellow.LOG.debug("Deep lighting install requested for {} (minY {})", world, BellowAPI.getMinY(world));
        if (BellowAPI.getMinY(world) >= 0) return;
        try {
            if (worldEngine == null) {
                worldEngine = fieldOfType(World.class, LightingEngine.class);
                chunkEngine = fieldOfType(Chunk.class, ILightingEngine.class);
            }
            Object current = worldEngine.get(world);
            // the engine fixes its range when built: a client world told a different floor later gets a new one
            if (current instanceof DeepPhosphorEngine
                && ((DeepPhosphorEngine) current).minY() == BellowAPI.getMinY(world)) return;
            if (current instanceof ILightingEngine) ((ILightingEngine) current).processLightUpdates();
            worldEngine.set(world, new DeepPhosphorEngine(world));
            for (Chunk chunk : loadedChunks(world.getChunkProvider())) chunkEngine.set(chunk, null);
            AkashicBellow.LOG.info(
                "Deep Phosphor lighting installed for dimension {} (remote: {})",
                world.provider.dimensionId,
                world.isRemote);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "ArchaicFix lighting layout is not the expected one; deep lighting cannot be installed",
                e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Chunk> loadedChunks(IChunkProvider provider) throws ReflectiveOperationException {
        if (provider instanceof ChunkProviderServer) return ((ChunkProviderServer) provider).loadedChunks;
        // ChunkProviderClient keeps its chunks in its only List field; referenced reflectively so this class stays
        // loadable on a dedicated server.
        if (provider != null && "net.minecraft.client.multiplayer.ChunkProviderClient".equals(
            provider.getClass()
                .getName())) {
            // Hodgepodge removes this list (null). Stale per-chunk caches are harmless: the old engine's queues are
            // drained before the swap and ArchaicFix only schedules Y >= 0 updates through them.
            List<Chunk> list = (List<Chunk>) fieldOfType(provider.getClass(), List.class).get(provider);
            if (list != null) return list;
        }
        return java.util.Collections.emptyList();
    }

    private static Field fieldOfType(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        Field found = null;
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type) {
                if (found != null)
                    throw new NoSuchFieldException("ambiguous " + type.getName() + " field in " + owner.getName());
                found = f;
            }
        }
        if (found == null) throw new NoSuchFieldException("no " + type.getName() + " field in " + owner.getName());
        found.setAccessible(true);
        return found;
    }
}
