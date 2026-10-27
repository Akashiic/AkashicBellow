package com.akashiic.bellow;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.akashiic.bellow.api.IDeepWorld;
import com.akashiic.bellow.world.ClientFloors;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/** Client thread side of the floor handshake. */
public final class ClientEvents {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        Integer announced = ClientFloors.get(world.provider.dimensionId);
        if (announced == null) return;
        IDeepWorld deep = (IDeepWorld) world;
        if (deep.bellow$getMinY() != announced) deep.bellow$setMinY(announced);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientFloors.clear();
    }
}
