package com.akashiic.bellow.selftest;

import java.lang.reflect.Method;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.akashiic.bellow.api.BellowAPI;

/**
 * BuildCraft's block property cache below Y 0 (only when BuildCraft is installed: the production pack). Reproduces the
 * kick of 2026-10-28: a block that changes below 0 in a chunk the cache covers ({@code World.markBlockForUpdate} ->
 * {@code DimensionProperty} -> {@code ChunkProperty.set} with a negative BitSet index). Then checks that the cache
 * reads
 * the deep layer: a block changed after the cache was made, and an air pocket that existed before it was made (the
 * cache's own fill loop). Through the public API ({@code BuildCraftAPI.getWorldProperty("soft")}), by reflection.
 */
public final class BuildCraftProbe {

    private BuildCraftProbe() {}

    public static void run(WorldServer world, BukkitProbe.Check check) {
        Object soft;
        Method get;
        try {
            soft = Class.forName("buildcraft.api.core.BuildCraftAPI")
                .getMethod("getWorldProperty", String.class)
                .invoke(null, "soft");
            get = Class.forName("buildcraft.api.core.IWorldProperty")
                .getMethod("get", World.class, int.class, int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            check.check("BuildCraft property cache", false, "API not found: " + e);
            return;
        }
        if (soft == null) {
            check.check("BuildCraft property cache", false, "no world property 'soft' registered");
            return;
        }
        int minY = BellowAPI.getMinY(world);
        int cx = 3100, cz = 3100, x = cx << 4, z = cz << 4;
        world.getChunkProvider()
            .loadChunk(cx, cz);
        world.getChunkProvider()
            .loadChunk(cx + 1, cz);
        // an air pocket before the cache of chunk cx+1 exists: read through the fill loop
        int pocket = minY + 12;
        world.setBlock(x + 16 + 3, pocket, z + 3, Blocks.air, 0, 3);
        world.setBlock(x + 1, -20, z + 1, Blocks.stone, 0, 3); // known content: the generated block may be an ore
        String error = null;
        boolean stoneBefore = true, airAfter = false, stoneAgain = true, filled = false, surface = false,
            edgeAir = false;
        try {
            // creates the cache of chunk cx (and fills it), as a robot or a builder looking at the chunk would
            surface = (Boolean) get.invoke(soft, world, x + 1, 250, z + 1);
            stoneBefore = (Boolean) get.invoke(soft, world, x + 1, -20, z + 1);
            // the kick: a block changing below 0 in a cached chunk (World.markBlockForUpdate -> ChunkProperty.set)
            // (setBlock notifies the listeners only once the chunk is populated and lit; a player's chunk is, a chunk
            // generated just now may not be, so the notification of the kick's stack trace is sent explicitly)
            world.setBlock(x + 1, -20, z + 1, Blocks.air, 0, 3);
            world.markBlockForUpdate(x + 1, -20, z + 1);
            airAfter = (Boolean) get.invoke(soft, world, x + 1, -20, z + 1);
            world.setBlock(x + 1, -20, z + 1, Blocks.stone, 0, 3);
            world.markBlockForUpdate(x + 1, -20, z + 1);
            stoneAgain = (Boolean) get.invoke(soft, world, x + 1, -20, z + 1);
            filled = (Boolean) get.invoke(soft, world, x + 16 + 3, pocket, z + 3);
            // the kick itself: local x 0, where BuildCraft's index x * 4096 + y * 16 + z goes negative (at other x it
            // silently lands on another block, as the reads above show without the fix)
            world.setBlock(x, -9, z + 5, Blocks.air, 0, 3);
            world.markBlockForUpdate(x, -9, z + 5);
            edgeAir = (Boolean) get.invoke(soft, world, x, -9, z + 5);
            // below the floor and above the top: nothing to read, no exception
            get.invoke(soft, world, x + 1, minY - 5, z + 1);
            get.invoke(soft, world, x + 1, 300, z + 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            error = cause.toString();
        }
        check.check(
            "BuildCraft property cache works below Y 0 (block changes do not throw)",
            error == null && !stoneBefore && airAfter && !stoneAgain && filled && surface && edgeAir,
            "exception " + error
                + "; soft at Y -20: stone "
                + stoneBefore
                + ", air "
                + airAfter
                + ", stone again "
                + stoneAgain
                + "; pocket at Y "
                + pocket
                + " made before the cache: "
                + filled
                + "; air at Y 250: "
                + surface
                + "; air at local x 0, Y -9: "
                + edgeAir);
    }
}
