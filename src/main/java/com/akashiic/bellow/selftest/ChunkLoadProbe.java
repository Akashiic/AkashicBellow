package com.akashiic.bellow.selftest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Phase {@code chunkload}: 3 x 3 chunks kept loaded by a Forge ticket (what chunk loader blocks do), far from any
 * player.
 * The same set of things is built at the surface and in the deep layer of the middle chunk and compared after
 * {@link #TICKS} ticks: a furnace smelting (tile entity ticks), ice next to glowstone melting (random ticks) and a
 * penned pig (entity updates). Another 3 x 3 area is only loaded, without a ticket, and is reported for information.
 * Report: {@code akashicbellow-chunkload.txt}.
 */
public final class ChunkLoadProbe {

    private static final int TICKS = 1200;
    private static final int ICE = 64;

    private final WorldServer world;
    private final List<String> out = new ArrayList<>();
    private int failures;
    private int ticks;
    private ForgeChunkManager.Ticket ticket;
    private final List<Site> sites = new ArrayList<>();

    /** One copy of the test set at one height of one chunk. */
    private static final class Site {

        final String name;
        final int x, y, z;
        final boolean forced;
        EntityPig pig;
        int pigTicks;

        Site(String name, int x, int y, int z, boolean forced) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.forced = forced;
        }
    }

    private ChunkLoadProbe(WorldServer world) {
        this.world = world;
    }

    /** Tickets need a loading callback registered during init; tickets left from an earlier run are released. */
    public static void registerCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(
            AkashicBellow.instance,
            (tickets, world) -> { for (ForgeChunkManager.Ticket t : tickets) ForgeChunkManager.releaseTicket(t); });
    }

    public static void start(MinecraftServer server) {
        ChunkLoadProbe probe = new ChunkLoadProbe(server.worldServerForDimension(0));
        try {
            probe.setup();
        } catch (Throwable e) {
            AkashicBellow.LOG.error("Chunk load probe setup failed", e);
            probe.check("setup", false, e.toString());
        }
        if (probe.ticket == null || probe.failures > 0) {
            probe.finish();
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .register(probe);
    }

    private void check(String name, boolean ok, String detail) {
        if (!ok) this.failures++;
        this.out.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
    }

    private void setup() {
        int minY = BellowAPI.getMinY(this.world);
        int cx = 1300, cz = 1300, ux = 1340;
        this.ticket = ForgeChunkManager
            .requestTicket(AkashicBellow.instance, this.world, ForgeChunkManager.Type.NORMAL);
        if (this.ticket == null) {
            // config/forgeChunkLoading.cfg gives mods without an entry the "defaults" quota, 0 on some servers
            this.check(
                "ticket",
                false,
                "Forge refused a ticket to akashicbellow: give it a quota in forgeChunkLoading.cfg");
            return;
        }
        // 3 x 3 chunks, like a common chunk loader: light is only updated where the neighbouring chunks are loaded
        // (World.updateLightByType), so a lone forced chunk keeps stale light at any height
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ForgeChunkManager.forceChunk(this.ticket, new ChunkCoordIntPair(cx + dx, cz + dz));
            this.world.theChunkProviderServer.loadChunk(cx + dx, cz + dz);
            this.world.theChunkProviderServer.loadChunk(ux + dx, cz + dz);
        }
        this.out.add(
            "INFO floor " + minY
                + ", forced chunks 3 x 3 around "
                + cx
                + ","
                + cz
                + ", players online "
                + this.world.playerEntities.size());
        this.sites.add(this.build("forced surface", cx * 16, 200, cz * 16, true));
        this.sites.add(this.build("forced deep", cx * 16, minY + 10, cz * 16, true));
        this.sites.add(this.build("unforced deep", ux * 16, minY + 10, cz * 16, false));
    }

    /**
     * Everything inside one chunk: a stone box 16 x 6 x 16, furnace at the corner, 8 x 8 ice under a glowstone ceiling,
     * a pig in the room.
     */
    private Site build(String name, int x, int y, int z, boolean forced) {
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int dy = -1; dy <= 4; dy++) {
            boolean shell = dy == -1 || dy == 4 || dx == 0 || dx == 15 || dz == 0 || dz == 15;
            this.world.setBlock(x + dx, y + dy, z + dz, shell ? Blocks.stone : Blocks.air, 0, 2);
        }
        for (int dx = 2; dx < 10; dx++) for (int dz = 2; dz < 10; dz++) {
            this.world.setBlock(x + dx, y, z + dz, Blocks.ice, 0, 2);
            this.world.setBlock(x + dx, y + 1, z + dz, Blocks.glowstone, 0, 2);
        }
        this.world.setBlock(x + 12, y, z + 2, Blocks.furnace, 0, 2);
        TileEntityFurnace furnace = (TileEntityFurnace) this.world.getTileEntity(x + 12, y, z + 2);
        furnace.setInventorySlotContents(0, new ItemStack(Blocks.iron_ore, 16));
        furnace.setInventorySlotContents(1, new ItemStack(Items.coal, 4));
        Site site = new Site(name, x, y, z, forced);
        site.pig = new EntityPig(this.world);
        site.pig.setLocationAndAngles(x + 12.5, y, z + 12.5, 0.0F, 0.0F);
        this.world.spawnEntityInWorld(site.pig);
        site.pigTicks = site.pig.ticksExisted;
        return site;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++this.ticks != TICKS) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        this.out.add("INFO players online " + this.world.playerEntities.size() + " after " + TICKS + " ticks");
        for (Site s : this.sites) this.report(s);
        ForgeChunkManager.releaseTicket(this.ticket);
        this.finish();
    }

    private void finish() {
        this.out.add(this.failures == 0 ? "RESULT PASS" : "RESULT FAIL (" + this.failures + ")");
        for (String line : this.out) AkashicBellow.LOG.info("[chunkload] {}", line);
        try {
            Files.write(new File("akashicbellow-chunkload.txt").toPath(), this.out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            AkashicBellow.LOG.error("Cannot write chunk load report", e);
        }
        if (Boolean.getBoolean("akashicbellow.selftest.exit")) MinecraftServer.getServer()
            .initiateShutdown();
    }

    private void report(Site s) {
        boolean loaded = this.world.theChunkProviderServer.chunkExists(s.x >> 4, s.z >> 4);
        TileEntityFurnace furnace = this.world.getTileEntity(s.x + 12, s.y, s.z + 2) instanceof TileEntityFurnace
            ? (TileEntityFurnace) this.world.getTileEntity(s.x + 12, s.y, s.z + 2)
            : null;
        ItemStack ingots = furnace == null ? null : furnace.getStackInSlot(2);
        int smelted = ingots == null ? 0 : ingots.stackSize;
        int melted = 0;
        for (int dx = 2; dx < 10; dx++) for (int dz = 2; dz < 10; dz++) {
            if (this.world.getBlock(s.x + dx, s.y, s.z + dz) != Blocks.ice) melted++;
        }
        int pigTicks = s.pig.ticksExisted - s.pigTicks;
        // ice melts when its block light is above 8 (BlockIce.updateTick); under glowstone it is 12
        int light = this.world.getSavedLightValue(EnumSkyBlock.Block, s.x + 3, s.y, s.z + 3);
        String detail = String.format(
            "chunk loaded %s, furnace smelted %d (200 ticks each), ice melted %d/%d (light %d), pig ticked %d/%d at y %.1f",
            loaded,
            smelted,
            melted,
            ICE,
            light,
            pigTicks,
            TICKS,
            s.pig.posY);
        if (!s.forced) {
            this.out.add("INFO " + s.name + " (no ticket, no player) - " + detail);
            return;
        }
        // 1200 ticks: 5 items smelted (6 if the first started at once); each ice block gets a random tick with
        // probability 1 - exp(-1200 * 3 / 4096) = 58%, so about 37 of 64 melt (fewer than 20: p < 1e-5)
        this.check(
            s.name,
            loaded && smelted >= 5
                && melted >= 20
                && pigTicks >= TICKS - 20
                && !s.pig.isDead
                && s.pig.posY > s.y - 0.5
                && s.pig.posY < s.y + 1.5,
            detail);
    }
}
