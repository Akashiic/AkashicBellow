package com.akashiic.bellow.selftest;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

/**
 * Crucible only: the Bukkit view of the deep layer, through the Bukkit API by reflection (no compile dependency).
 * A known block is placed at Y -10 and a different one at Y 246, the column the 0xFF mask used to fold -10 onto.
 */
public final class BukkitProbe {

    public interface Check {

        void check(String name, boolean ok, String detail);
    }

    private BukkitProbe() {}

    public static void run(WorldServer world, int x, int z, Check out) throws ReflectiveOperationException {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
        Object bworld = ((List<?>) bukkit.getMethod("getWorlds")
            .invoke(null)).stream()
                .filter(w -> {
                    try {
                        return w.getClass()
                            .getMethod("getHandle")
                            .invoke(w) == world;
                    } catch (ReflectiveOperationException e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no Bukkit world for " + world));
        Method getBlockAt = bworld.getClass()
            .getMethod("getBlockAt", int.class, int.class, int.class);

        world.setBlock(x, -10, z, Blocks.diamond_block, 0, 3);
        world.setBlock(x, 246, z, Blocks.gold_block, 0, 3);
        world.setBlock(x, -9, z, Blocks.glowstone, 0, 3);
        world.setBlock(x, -8, z, Blocks.air, 0, 3); // light is read in the air above the glowstone
        Object block = getBlockAt.invoke(bworld, x, -10, z);
        int by = (Integer) block.getClass()
            .getMethod("getY")
            .invoke(block);
        int id = (Integer) block.getClass()
            .getMethod("getTypeId")
            .invoke(block);
        out.check("bukkit getBlockAt keeps Y below 0", by == -10, "getY=" + by);
        out.check(
            "bukkit block type below 0",
            id == Block.getIdFromBlock(Blocks.diamond_block),
            "typeId=" + id + " (gold at Y 246 would be " + Block.getIdFromBlock(Blocks.gold_block) + ")");
        Object above = getBlockAt.invoke(bworld, x, -11, z);
        int light = (Byte) above.getClass()
            .getMethod("getLightFromBlocks")
            .invoke(getBlockAt.invoke(bworld, x, -8, z));
        net.minecraft.world.EnumSkyBlock B = net.minecraft.world.EnumSkyBlock.Block;
        int nms = world.getSavedLightValue(B, x, -8, z);
        // Diagnostics: light at the source, after a forced update, and with the opposite placement order.
        int atSource = world.getSavedLightValue(B, x, -9, z);
        boolean updated = world.updateLightByType(B, x, -8, z);
        int forced = world.getSavedLightValue(B, x, -8, z);
        world.setBlock(x + 2, -8, z, Blocks.air, 0, 3);
        world.setBlock(x + 2, -9, z, Blocks.glowstone, 0, 3);
        int reversed = world.getSavedLightValue(B, x + 2, -8, z);
        world.setBlockToAir(x + 2, -9, z);
        // Same scenario above ground in the same chunk, and below 0 in a chunk loaded after world creation.
        world.setBlock(x, 200, z, Blocks.glowstone, 0, 3);
        int surface = world.getSavedLightValue(B, x, 201, z);
        world.setBlockToAir(x, 200, z);
        int fx = 16 * 1010 + 4, fz = 16 * 1010 + 4;
        world.getChunkFromChunkCoords(1010, 1010);
        for (int cx = 1009; cx <= 1011; cx++)
            for (int cz = 1009; cz <= 1011; cz++) world.getChunkFromChunkCoords(cx, cz);
        world.setBlock(fx, -8, fz, Blocks.air, 0, 3);
        world.setBlock(fx, -9, fz, Blocks.glowstone, 0, 3);
        int farDeep = world.getSavedLightValue(B, fx, -8, fz);
        world.setBlockToAir(fx, -9, fz);
        String spawnNeighbours;
        try {
            Object c = world.getChunkFromBlockCoords(x, z);
            spawnNeighbours = String.valueOf(
                c.getClass()
                    .getMethod("areNeighborsLoaded", int.class)
                    .invoke(c, 1));
        } catch (ReflectiveOperationException e) {
            spawnNeighbours = "n/a";
        }
        out.check(
            "light in the probe chunk also fails above ground (Crucible spawn chunk, not deep-specific)",
            nms != 0 || surface == 0,
            "surface=" + surface + " farDeep=" + farDeep + " spawnChunk.areNeighborsLoaded(1)=" + spawnNeighbours);
        out.check("bukkit-independent: deep light in a chunk loaded after start", farDeep >= 14, "farDeep=" + farDeep);
        out.check(
            "bukkit block light below 0",
            light >= 14 && light == nms,
            "bukkit=" + light
                + " world="
                + nms
                + " source="
                + atSource
                + " forcedUpdate="
                + updated
                + "->"
                + forced
                + " airFirst="
                + reversed);

        Block before245 = world.getBlock(x, 245, z);
        above.getClass()
            .getMethod("setTypeId", int.class)
            .invoke(above, Block.getIdFromBlock(Blocks.emerald_block));
        out.check(
            "bukkit setTypeId below 0 writes there",
            world.getBlock(x, -11, z) == Blocks.emerald_block && world.getBlock(x, 245, z) == before245,
            "at -11: " + Block.blockRegistry.getNameForObject(world.getBlock(x, -11, z))
                + ", at 245: "
                + Block.blockRegistry.getNameForObject(world.getBlock(x, 245, z)));
        for (int y : new int[] { -11, -10, -9, -8, 246 }) world.setBlockToAir(x, y, z);
    }
}
