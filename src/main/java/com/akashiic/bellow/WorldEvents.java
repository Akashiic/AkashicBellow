package com.akashiic.bellow;

import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.akashiic.bellow.compat.archaic.DeepLightingInstaller;
import com.akashiic.bellow.compat.bukkit.BukkitFloors;
import com.akashiic.bellow.core.Compat;
import com.akashiic.bellow.storage.DeepTiles;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Forge bus listeners. */
public final class WorldEvents {

    /**
     * Server worlds get the deep Phosphor engine as soon as they exist (before the spawn area is loaded). Client worlds
     * get it when the server announces their floor. On Crucible the floor is also published for Bukkit plugins.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.world;
        if (world.isRemote) return;
        if (Compat.phosphor) DeepLightingInstaller.install(world);
        if (Compat.crucible) BukkitFloors.publish(world);
    }

    /** Runs last so other listeners still see the chunk as written; see {@link DeepTiles}. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChunkSave(ChunkDataEvent.Save event) {
        DeepTiles.strip(event.getChunk(), event.getData());
    }
}
