package com.akashiic.bellow.compat.angelica;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.akashiic.bellow.api.BellowAPI;

/** Floor of the client world as seen by the Celeritas renderer (render threads included). */
public final class AngelicaFloor {

    private AngelicaFloor() {}

    public static int current() {
        World world = Minecraft.getMinecraft().theWorld;
        return world == null ? 0 : BellowAPI.getMinY(world);
    }
}
