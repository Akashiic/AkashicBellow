package com.akashiic.bellow.selftest;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.compat.bukkit.BukkitFloors;

/**
 * Crucible only: the protection and editing plugins of the pack below Y 0, through their own APIs by reflection (each
 * from its plugin class loader). Plugins that are not installed are reported and skipped.
 * <ul>
 * <li>WorldEdit: the agent is active, a cuboid selection reaches the floor (not 0), an EditSession fills it, and a
 * block under the floor is still refused.</li>
 * <li>WorldGuard: a region below 0 is found by a point query inside it and not above it.</li>
 * <li>GriefPreventionPlus: claims are columns cut at {@code GriefPrevention.Claims.MaximumDepth}; a new claim must be
 * found both at the surface and near the floor, which needs that setting at or below the floor.</li>
 * </ul>
 */
public final class PluginProbe {

    private static final int CHUNK = 1020;
    private static final String REGION = "akashicbellow_probe";

    private PluginProbe() {}

    public static void run(WorldServer world, BukkitProbe.Check out) throws ReflectiveOperationException {
        int minY = BellowAPI.getMinY(world);
        Object bworld = BukkitProbe.bukkitWorld(world);
        String name = (String) bworld.getClass()
            .getMethod("getName")
            .invoke(bworld);
        String published = System.getProperty(BukkitFloors.PREFIX + name);
        out.check(
            "floor published to Bukkit plugins",
            Integer.toString(minY)
                .equals(published),
            name + "=" + published);

        for (int cx = CHUNK - 1; cx <= CHUNK + 1; cx++) {
            for (int cz = CHUNK - 1; cz <= CHUNK + 1; cz++) world.getChunkFromChunkCoords(cx, cz);
        }
        int x = CHUNK * 16 + 4, z = CHUNK * 16 + 4;
        Object plugins = Class.forName("org.bukkit.Bukkit")
            .getMethod("getPluginManager")
            .invoke(null);
        Object we = plugin(plugins, "WorldEdit");
        if (we == null) out.check("WorldEdit", true, "not installed, skipped");
        else worldEdit(
            world,
            bworld,
            we.getClass()
                .getClassLoader(),
            minY,
            x,
            z,
            out);
        Object wg = plugin(plugins, "WorldGuard");
        if (wg == null) out.check("WorldGuard", true, "not installed, skipped");
        else worldGuard(bworld, wg, minY, x, z, out);
        Object gpp = plugin(plugins, "GriefPreventionPlus");
        if (gpp == null) out.check("GriefPreventionPlus", true, "not installed, skipped");
        else griefPrevention(bworld, gpp, minY, x, z, out);
    }

    private static void worldEdit(WorldServer world, Object bworld, ClassLoader cl, int minY, int x, int z,
        BukkitProbe.Check out) throws ReflectiveOperationException {
        Class<?> cuboid = cl.loadClass("com.sk89q.worldedit.regions.CuboidRegion");
        Class<?> weWorldType = cl.loadClass("com.sk89q.worldedit.world.World");
        boolean agent = false;
        for (java.lang.reflect.Method m : cuboid.getDeclaredMethods()) agent |= m.getName()
            .equals("akashicbellow$minY");
        out.check(
            "WorldEdit agent active",
            agent,
            agent ? "" : "CuboidRegion is not patched: check the -javaagent flag and the [AkashicBellow-WE] log lines");
        if (!agent) return;

        Object weWorld = cl.loadClass("com.sk89q.worldedit.bukkit.BukkitWorld")
            .getConstructor(cl.loadClass("org.bukkit.World"))
            .newInstance(bworld);
        Object region = cuboid.getConstructor(weWorldType, vectorType(cl), vectorType(cl))
            .newInstance(weWorld, vec(cl, x, minY - 30, z), vec(cl, x + 2, minY + 10, z + 2));
        Object min = cuboid.getMethod("getMinimumPoint")
            .invoke(region);
        int selMin = (Integer) min.getClass()
            .getMethod("getBlockY")
            .invoke(min);
        out.check("WorldEdit selection reaches the floor", selMin == minY, "minY=" + selMin);

        Object weInstance = cl.loadClass("com.sk89q.worldedit.WorldEdit")
            .getMethod("getInstance")
            .invoke(null);
        Object factory = cl.loadClass("com.sk89q.worldedit.WorldEdit")
            .getMethod("getEditSessionFactory")
            .invoke(weInstance);
        Object session = cl.loadClass("com.sk89q.worldedit.EditSessionFactory")
            .getMethod("getEditSession", weWorldType, int.class)
            .invoke(factory, weWorld, -1);
        Class<?> baseBlock = cl.loadClass("com.sk89q.worldedit.blocks.BaseBlock");
        Object emerald = baseBlock.getConstructor(int.class)
            .newInstance(Block.getIdFromBlock(Blocks.emerald_block));
        int changed = (Integer) cl.loadClass("com.sk89q.worldedit.EditSession")
            .getMethod("setBlocks", cl.loadClass("com.sk89q.worldedit.regions.Region"), baseBlock)
            .invoke(session, region, emerald);
        boolean below = (Boolean) cl.loadClass("com.sk89q.worldedit.EditSession")
            .getMethod("setBlock", vectorType(cl), baseBlock)
            .invoke(session, vec(cl, x, minY - 1, z), emerald);
        cl.loadClass("com.sk89q.worldedit.EditSession")
            .getMethod("flushQueue")
            .invoke(session);
        boolean floor = world.getBlock(x, minY, z) == Blocks.emerald_block;
        boolean top = world.getBlock(x + 2, minY + 10, z + 2) == Blocks.emerald_block;
        out.check(
            "WorldEdit edits below 0",
            changed == 3 * 3 * 11 && floor && top,
            "changed=" + changed + " (expected 99), at floor: " + floor + ", at floor+10: " + top);
        out.check("WorldEdit refuses below the floor", !below, "setBlock(y=" + (minY - 1) + ")=" + below);
        for (int y = minY; y <= minY + 10; y++) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 2; dz++) world.setBlockToAir(x + dx, y, z + dz);
            }
        }
    }

    private static void worldGuard(Object bworld, Object wg, int minY, int x, int z, BukkitProbe.Check out)
        throws ReflectiveOperationException {
        ClassLoader cl = wg.getClass()
            .getClassLoader();
        Object manager = wg.getClass()
            .getMethod("getRegionManager", cl.loadClass("org.bukkit.World"))
            .invoke(wg, bworld);
        Class<?> blockVector = cl.loadClass("com.sk89q.worldedit.BlockVector");
        java.lang.reflect.Constructor<?> bv = blockVector.getConstructor(int.class, int.class, int.class);
        Object region = cl.loadClass("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion")
            .getConstructor(String.class, blockVector, blockVector)
            .newInstance(REGION, bv.newInstance(x, minY + 2, z), bv.newInstance(x + 2, minY + 20, z + 2));
        Class<?> protectedRegion = cl.loadClass("com.sk89q.worldguard.protection.regions.ProtectedRegion");
        manager.getClass()
            .getMethod("addRegion", protectedRegion)
            .invoke(manager, region);
        try {
            int inside = applicable(manager, cl, x + 1, minY + 5, z + 1);
            int above = applicable(manager, cl, x + 1, minY + 25, z + 1);
            out.check("WorldGuard region below 0", inside == 1 && above == 0, "inside=" + inside + " above=" + above);
        } finally {
            manager.getClass()
                .getMethod("removeRegion", String.class)
                .invoke(manager, REGION);
        }
    }

    private static void griefPrevention(Object bworld, Object gpp, int minY, int x, int z, BukkitProbe.Check out)
        throws ReflectiveOperationException {
        ClassLoader cl = gpp.getClass()
            .getClassLoader();
        Object config = gpp.getClass()
            .getField("config")
            .get(gpp);
        int depth = config.getClass()
            .getField("claims_maxDepth")
            .getInt(config);
        Object store = gpp.getClass()
            .getMethod("getDataStore")
            .invoke(gpp);
        Class<?> claimType = cl.loadClass("net.kaikk.mc.gpp.Claim");
        Class<?> location = cl.loadClass("org.bukkit.Location");
        java.util.UUID worldId = (java.util.UUID) bworld.getClass()
            .getMethod("getUID")
            .invoke(bworld);
        Object result = cl.loadClass("net.kaikk.mc.gpp.DataStore")
            .getMethod(
                "newClaim",
                java.util.UUID.class,
                int.class,
                int.class,
                int.class,
                int.class,
                java.util.UUID.class,
                claimType,
                Integer.class,
                cl.loadClass("org.bukkit.entity.Player"))
            .invoke(store, worldId, x, z, x + 20, z + 20, java.util.UUID.randomUUID(), null, null, null);
        Object claim = result.getClass()
            .getMethod("getClaim")
            .invoke(result);
        Object status = result.getClass()
            .getMethod("getResult")
            .invoke(result);
        out.check("GriefPreventionPlus claim created", claim != null, "result=" + status);
        if (claim == null) return;
        try {
            java.lang.reflect.Constructor<?> loc = location
                .getConstructor(cl.loadClass("org.bukkit.World"), double.class, double.class, double.class);
            java.lang.reflect.Method at = cl.loadClass("net.kaikk.mc.gpp.DataStore")
                .getMethod("getClaimAt", location, boolean.class);
            Object surface = at.invoke(store, loc.newInstance(bworld, x + 5.5, 70.0, z + 5.5), false);
            Object deep = at.invoke(store, loc.newInstance(bworld, x + 5.5, minY + 5.0, z + 5.5), false);
            out.check("GriefPreventionPlus claim at the surface", surface == claim, "found=" + (surface != null));
            out.check(
                "GriefPreventionPlus claim reaches the floor",
                depth <= minY && deep == claim,
                "MaximumDepth=" + depth
                    + ", found at y="
                    + (minY + 5)
                    + ": "
                    + (deep != null)
                    + (depth > minY ? " (set GriefPrevention.Claims.MaximumDepth to " + minY + ")" : ""));
        } finally {
            cl.loadClass("net.kaikk.mc.gpp.DataStore")
                .getMethod("deleteClaim", claimType)
                .invoke(store, claim);
        }
    }

    private static int applicable(Object manager, ClassLoader cl, int x, int y, int z)
        throws ReflectiveOperationException {
        Object set = manager.getClass()
            .getMethod("getApplicableRegions", vectorType(cl))
            .invoke(manager, vec(cl, x, y, z));
        int n = 0;
        for (Object r : (java.util.Set<?>) set.getClass()
            .getMethod("getRegions")
            .invoke(set)) {
            if (REGION.equals(
                r.getClass()
                    .getMethod("getId")
                    .invoke(r)))
                n++;
        }
        return n;
    }

    private static Object plugin(Object plugins, String name) throws ReflectiveOperationException {
        Object p = plugins.getClass()
            .getMethod("getPlugin", String.class)
            .invoke(plugins, name);
        return p != null && (Boolean) p.getClass()
            .getMethod("isEnabled")
            .invoke(p) ? p : null;
    }

    private static Class<?> vectorType(ClassLoader cl) throws ClassNotFoundException {
        return cl.loadClass("com.sk89q.worldedit.Vector");
    }

    private static Object vec(ClassLoader cl, int x, int y, int z) throws ReflectiveOperationException {
        return vectorType(cl).getConstructor(int.class, int.class, int.class)
            .newInstance(x, y, z);
    }
}
