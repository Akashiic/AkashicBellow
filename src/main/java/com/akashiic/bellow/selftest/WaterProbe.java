package com.akashiic.bellow.selftest;

import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

/**
 * Swimming out of water onto a ledge, below 0 and above (the control): a player in a pool two blocks deep, pressed
 * against the side with jump held, runs vanilla's movement tick by tick. It gets out when
 * {@code Entity.isOffsetPositionInLiquid} finds no liquid in its box raised by 0.6, through {@code World.isAnyLiquid},
 * which also scans the block under the box when its minimum Y is negative.
 */
public final class WaterProbe {

    private static final int CX = 4400;

    private WaterProbe() {}

    public static void run(WorldServer world, int minY, BukkitProbe.Check check) {
        for (int cx = CX - 1; cx <= CX + 1; cx++) for (int cz = CX - 1; cz <= CX + 1; cz++) world.getChunkProvider()
            .loadChunk(cx, cz);
        int x0 = (CX << 4) + 8, z0 = (CX << 4) + 8;
        String surface = swimOut(world, x0, 100, z0, 0);
        check.check("swims out of water onto a ledge (control, Y 100)", surface.startsWith(OUT), surface);
        String deep = swimOut(world, x0, minY + 30, z0, 1);
        check.check("swims out of water onto a ledge below 0 (Y " + (minY + 30) + ")", deep.startsWith(OUT), deep);
    }

    private static final String OUT = "on the ledge";

    /** A 5 x 5 pool of water at y0..y0+1 with stone around up to y0+1 and air above; where the player ended up. */
    private static String swimOut(WorldServer world, int x0, int y0, int z0, int n) {
        for (int x = x0 - 4; x <= x0 + 4; x++) for (int z = z0 - 4; z <= z0 + 4; z++) {
            for (int y = y0 - 1; y <= y0 + 5; y++)
                world.setBlock(x, y, z, y <= y0 + 1 ? Blocks.stone : Blocks.air, 0, 2);
        }
        for (int x = x0 - 2; x <= x0 + 2; x++) for (int z = z0 - 2; z <= z0 + 2; z++) {
            for (int y = y0; y <= y0 + 1; y++) world.setBlock(x, y, z, Blocks.water, 0, 2);
        }
        FakePlayer p = new FakePlayer(
            world,
            new GameProfile(UUID.nameUUIDFromBytes(("bellow-swim" + n).getBytes()), "[BellowSwim]"));
        p.setPositionAndRotation(x0 + 0.5, y0 + 1.0, z0 + 0.5, -90.0F, 0.0F); // facing +x, the wall at x0 + 3
        int ticks = 0;
        boolean out = false;
        for (; ticks < 200 && !out; ticks++) {
            p.moveForward = 1.0F;
            p.setJumping(true);
            p.handleWaterMovement();
            p.onLivingUpdate();
            out = p.posY >= y0 + 2 && p.posX > x0 + 3 && !p.isInWater();
        }
        return String.format(
            "%s after %d ticks, at %.2f %.2f %.2f (pool %d..%d, ledge top %d)",
            out ? OUT : "still in the pool",
            ticks,
            p.posX - x0,
            p.posY,
            p.posZ - z0,
            y0,
            y0 + 1,
            y0 + 2);
    }
}
