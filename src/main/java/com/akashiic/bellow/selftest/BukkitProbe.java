package com.akashiic.bellow.selftest;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;

/**
 * Crucible only: the Bukkit view of the deep layer, through the Bukkit API by reflection (no compile dependency).
 * A known block is placed at Y -10 and a different one at Y 246, the column the 0xFF mask used to fold -10 onto.
 * <p>
 * Runs in chunks loaded after startup. In a new world Crucible generates the first chunks inside the World
 * constructor, before CraftBukkit tracks their neighbours; {@code updateLightByType} then refuses every light update
 * in them at any height ({@code areNeighborsLoaded(1)} is false; measured above ground too). Existing worlds load
 * their spawn chunks through the regular path.
 */
public final class BukkitProbe {

    public interface Check {

        void check(String name, boolean ok, String detail);
    }

    private static final int CHUNK = 1010;

    private BukkitProbe() {}

    public static void run(WorldServer world, Check out) throws ReflectiveOperationException {
        for (int cx = CHUNK - 1; cx <= CHUNK + 1; cx++) {
            for (int cz = CHUNK - 1; cz <= CHUNK + 1; cz++) world.getChunkFromChunkCoords(cx, cz);
        }
        int x = CHUNK * 16 + 4, z = CHUNK * 16 + 4;
        Object bworld = bukkitWorld(world);
        Method getBlockAt = bworld.getClass()
            .getMethod("getBlockAt", int.class, int.class, int.class);

        world.setBlock(x, -10, z, Blocks.diamond_block, 0, 3);
        world.setBlock(x, 246, z, Blocks.gold_block, 0, 3);
        world.setBlock(x, -8, z, Blocks.air, 0, 3);
        world.setBlock(x, -9, z, Blocks.glowstone, 0, 3);

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

        Object lit = getBlockAt.invoke(bworld, x, -8, z);
        int light = (Byte) lit.getClass()
            .getMethod("getLightFromBlocks")
            .invoke(lit);
        int world8 = world.getSavedLightValue(EnumSkyBlock.Block, x, -8, z);
        out.check("bukkit block light below 0", light >= 14 && light == world8, "bukkit=" + light + " world=" + world8);

        Object below = getBlockAt.invoke(bworld, x, -11, z);
        Block before245 = world.getBlock(x, 245, z);
        below.getClass()
            .getMethod("setTypeId", int.class)
            .invoke(below, Block.getIdFromBlock(Blocks.emerald_block));
        out.check(
            "bukkit setTypeId below 0 writes there",
            world.getBlock(x, -11, z) == Blocks.emerald_block && world.getBlock(x, 245, z) == before245,
            "at -11: " + Block.blockRegistry.getNameForObject(world.getBlock(x, -11, z))
                + ", at 245: "
                + Block.blockRegistry.getNameForObject(world.getBlock(x, 245, z)));
        for (int y : new int[] { -11, -10, -9, -8, 246 }) world.setBlockToAir(x, y, z);
    }

    static Object bukkitWorld(WorldServer world) throws ReflectiveOperationException {
        for (Object w : (List<?>) Class.forName("org.bukkit.Bukkit")
            .getMethod("getWorlds")
            .invoke(null)) {
            if (w.getClass()
                .getMethod("getHandle")
                .invoke(w) == world) return w;
        }
        throw new IllegalStateException("no Bukkit world for " + world);
    }
}
