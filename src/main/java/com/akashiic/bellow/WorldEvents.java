package com.akashiic.bellow;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import com.akashiic.bellow.compat.archaic.DeepLightingInstaller;
import com.akashiic.bellow.core.Compat;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Forge bus listeners. */
public final class WorldEvents {

    /**
     * Server worlds get the deep Phosphor engine as soon as they exist (before the spawn area is loaded). Client worlds
     * get it when the server announces their floor.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.world;
        if (Compat.phosphor && !world.isRemote) DeepLightingInstaller.install(world);
    }
}
