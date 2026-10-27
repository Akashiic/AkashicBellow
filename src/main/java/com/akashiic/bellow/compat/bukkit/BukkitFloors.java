package com.akashiic.bellow.compat.bukkit;

import java.lang.reflect.Method;

import net.minecraft.world.World;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

/**
 * Crucible only: publishes the floor of each server world as the system property
 * {@code akashicbellow.floor.<Bukkit world name>}, read by plugin-side patches (the WorldEdit agent) that cannot see
 * mod classes from their own class loader. Absent means "no deep layer" (floor 0).
 */
public final class BukkitFloors {

    public static final String PREFIX = "akashicbellow.floor.";
    private static final String CRAFT_WORLD = "org.bukkit.craftbukkit.v1_7_R4.CraftWorld";
    private static boolean warned;

    private BukkitFloors() {}

    public static void publish(World world) {
        String name = bukkitName(world);
        if (name == null) return;
        int minY = BellowAPI.getMinY(world);
        if (minY < 0) System.setProperty(PREFIX + name, Integer.toString(minY));
        else System.clearProperty(PREFIX + name);
    }

    /** The CraftWorld name: CraftBukkit adds a no-argument accessor returning the CraftWorld to every World. */
    public static String bukkitName(World world) {
        try {
            for (Method m : world.getClass()
                .getMethods()) {
                if (m.getParameterTypes().length != 0 || !m.getReturnType()
                    .getName()
                    .equals(CRAFT_WORLD)) continue;
                Object craft = m.invoke(world);
                if (craft != null) return (String) craft.getClass()
                    .getMethod("getName")
                    .invoke(craft);
            }
            warn("no CraftWorld for " + world.provider.dimensionId, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("cannot read the Bukkit name of dimension " + world.provider.dimensionId, e);
        }
        return null;
    }

    private static void warn(String message, Throwable t) {
        if (warned) return;
        warned = true;
        AkashicBellow.LOG.warn("Bukkit floor not published ({}); WorldEdit keeps Y 0 as its floor", message, t);
    }
}
