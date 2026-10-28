package com.akashiic.bellow;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.akashiic.bellow.api.IDeepWorld;
import com.akashiic.bellow.world.ClientFloors;
import com.akashiic.bellow.world.IDeepWorldInternal;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Client thread side of the floor handshake. */
public final class ClientEvents {

    /** The client world (and floor) the deep lighting engine was last installed for; weak: an old world must go. */
    private java.lang.ref.WeakReference<World> litWorld = new java.lang.ref.WeakReference<>(null);
    private int litMinY;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        Integer announced = ClientFloors.get(world.provider.dimensionId);
        IDeepWorld deep = (IDeepWorld) world;
        if (announced != null && deep.bellow$getMinY() != announced)
            ((IDeepWorldInternal) world).bellow$setMinY(announced);
        // A world that read its floor from ClientFloors when it was created (re-entering a dimension, or the floor
        // message before S01) never went through setMinY: give it the deep lighting engine here, once per world/floor.
        int minY = deep.bellow$getMinY();
        if (minY < 0 && com.akashiic.bellow.core.Compat.phosphor
            && (world != this.litWorld.get() || minY != this.litMinY)) {
            com.akashiic.bellow.compat.archaic.DeepLightingInstaller.install(world);
            this.litWorld = new java.lang.ref.WeakReference<>(world);
            this.litMinY = minY;
        }
        if (ClientFloors.consumeRendererDirty() && Minecraft.getMinecraft().renderGlobal != null) {
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientFloors.clear();
        this.litWorld = new java.lang.ref.WeakReference<>(null);
    }
}
