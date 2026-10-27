package com.akashiic.bellow.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import org.apache.commons.io.FileUtils;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Headless client verification ({@code -Dakashicbellow.clienttest=true}): creates a single player world, builds a lit
 * room below Y 0, compares what the client received with the server, checks wall torch placement and takes
 * screenshots, then quits. Report: {@code akashicbellow-clienttest.txt} in the game directory.
 */
public final class ClientSelfTest {

    private static final String SAVE = "bellow-clienttest";
    private final List<String> out = new ArrayList<>();
    private int state;
    private int timer;
    private int failures;
    private volatile boolean serverReady;
    private int x0, y0, z0;

    public static void register() {
        ClientSelfTest test = new ClientSelfTest();
        FMLCommonHandler.instance()
            .bus()
            .register(test);
    }

    private void check(String name, boolean ok, String detail) {
        if (!ok) this.failures++;
        this.out.add((ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " - " + detail));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            switch (this.state) {
                case 0:
                    if (mc.currentScreen instanceof GuiMainMenu) {
                        FileUtils.deleteDirectory(new File(mc.mcDataDir, "saves/" + SAVE));
                        WorldSettings settings = new WorldSettings(
                            20261027L,
                            WorldSettings.GameType.CREATIVE,
                            true,
                            false,
                            WorldType.DEFAULT);
                        mc.launchIntegratedServer(SAVE, SAVE, settings);
                        this.state = 1;
                    }
                    break;
                case 1:
                    if (mc.theWorld != null && mc.thePlayer != null && ++this.timer > 40) {
                        this.state = 2;
                        this.timer = 0;
                    }
                    break;
                case 2: // server builds the room on its own thread
                    if (this.serverReady && ++this.timer > 400) {
                        this.verifyClient(mc);
                        this.shot(mc, "bellow-deep-room");
                        this.state = 3;
                        this.timer = 0;
                    }
                    break;
                case 3:
                    if (++this.timer > 5) {
                        mc.thePlayer.rotationPitch = 60.0F;
                        this.state = 4;
                        this.timer = 0;
                    }
                    break;
                case 4:
                    if (++this.timer > 60) {
                        this.shot(mc, "bellow-deep-floor");
                        this.surfaceRequested = true;
                        this.state = 5;
                        this.timer = 0;
                    }
                    break;
                case 5: // control shot: a chest on the surface, to tell deep-specific issues from renderer ones
                    if (this.surfaceReady && ++this.timer > 300) {
                        this.shot(mc, "bellow-surface-control");
                        this.finish(mc);
                    }
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            AkashicBellow.LOG.error("Client self test crashed", t);
            this.check("no exception", false, t.toString());
            this.finish(mc);
        }
    }

    private volatile boolean surfaceRequested;
    private volatile boolean surfaceReady;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.surfaceRequested && !this.surfaceReady) {
            WorldServer w = MinecraftServer.getServer()
                .worldServerForDimension(0);
            EntityPlayerMP p = (EntityPlayerMP) w.playerEntities.get(0);
            int top = w.getTopSolidOrLiquidBlock(this.x0, this.z0);
            w.setBlock(this.x0, top, this.z0 + 3, Blocks.chest);
            w.setBlock(this.x0 - 2, top, this.z0 + 3, Blocks.wool, 14, 3);
            p.playerNetServerHandler.setPlayerLocation(this.x0 + 0.5, top, this.z0 - 0.5, 0.0F, 30.0F);
            this.surfaceReady = true;
            return;
        }
        if (event.phase != TickEvent.Phase.END || this.state != 2 || this.serverReady) return;
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer world = server.worldServerForDimension(0);
        if (world.playerEntities.isEmpty()) return;
        EntityPlayerMP player = (EntityPlayerMP) world.playerEntities.get(0);
        int minY = BellowAPI.getMinY(world);
        ChunkCoordinates spawn = world.getSpawnPoint();
        this.x0 = spawn.posX;
        this.z0 = spawn.posZ;
        this.y0 = minY + 24;
        // 15 x 7 x 15 room, glowstone in the corners and the ceiling centre
        for (int dx = -7; dx <= 7; dx++) for (int dy = 0; dy < 7; dy++) for (int dz = -7; dz <= 7; dz++) {
            world.setBlockToAir(this.x0 + dx, this.y0 + dy, this.z0 + dz);
        }
        for (int dy = 0; dy < 7; dy++) for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            world.setBlock(this.x0 + 7 * sx, this.y0 + dy, this.z0 + 7 * sz, Blocks.glowstone);
        }
        world.setBlock(this.x0, this.y0 + 6, this.z0, Blocks.glowstone);
        world.setBlock(this.x0 + 2, this.y0, this.z0 + 3, Blocks.chest);
        world.setBlock(this.x0 - 2, this.y0, this.z0 + 3, Blocks.wool, 14, 3);

        // Wall torch placed the way a player does it: right click the north face (side 2) of the south wall block.
        int wx = this.x0, wy = this.y0 + 2, wz = this.z0 + 8;
        ItemStack torch = new ItemStack(Blocks.torch);
        boolean used = torch.getItem()
            .onItemUse(torch, player, world, wx, wy, wz, 2, 0.5F, 0.5F, 0.0F);
        int meta = world.getBlockMetadata(wx, wy, wz - 1);
        Block placed = world.getBlock(wx, wy, wz - 1);
        // BlockTorch.onBlockPlaced: side 2 -> meta 4 (attached to the block at z + 1)
        this.check(
            "wall torch placement (server)",
            used && placed == Blocks.torch && meta == 4,
            "block=" + Block.blockRegistry.getNameForObject(placed) + " meta=" + meta);

        player.setPositionAndUpdate(this.x0 + 0.5, this.y0 + 0.0, this.z0 - 5.5);
        player.rotationYaw = 0.0F; // facing south (+z)
        player.rotationPitch = 10.0F;
        player.playerNetServerHandler.setPlayerLocation(this.x0 + 0.5, this.y0, this.z0 - 5.5, 0.0F, 10.0F);
        world.setWorldTime(6000);
        this.serverReady = true;
    }

    private void verifyClient(Minecraft mc) {
        WorldServer server = MinecraftServer.getServer()
            .worldServerForDimension(0);
        int minY = BellowAPI.getMinY(server);
        this.check(
            "client floor",
            BellowAPI.getMinY(mc.theWorld) == minY,
            "client=" + BellowAPI.getMinY(mc.theWorld) + " server=" + minY);
        int mismatches = 0, compared = 0;
        for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) for (int y = minY; y < 0; y += 3) {
            int x = this.x0 + dx, z = this.z0 + dz;
            compared++;
            if (mc.theWorld.getBlock(x, y, z) != server.getBlock(x, y, z)
                || mc.theWorld.getBlockMetadata(x, y, z) != server.getBlockMetadata(x, y, z)) mismatches++;
        }
        this.check(
            "client blocks match server below Y 0",
            mismatches == 0,
            mismatches + "/" + compared + " mismatches");
        int tx = this.x0, ty = this.y0 + 2, tz = this.z0 + 7;
        this.check(
            "wall torch on client",
            mc.theWorld.getBlock(tx, ty, tz) == Blocks.torch && mc.theWorld.getBlockMetadata(tx, ty, tz) == 4,
            "meta=" + mc.theWorld.getBlockMetadata(tx, ty, tz));
        // Read the chunk map directly: World.getTileEntity would create a missing tile entity lazily on this thread,
        // which is exactly what renderers that snapshot the map (Angelica) never do.
        int cx = this.x0 + 2, cz = this.z0 + 3;
        Object chestTe = mc.theWorld.getChunkFromBlockCoords(cx, cz).chunkTileEntityMap
            .get(new net.minecraft.world.ChunkPosition(cx & 15, this.y0, cz & 15));
        this.check("client tile entity below Y 0 (created on receipt)", chestTe != null, String.valueOf(chestTe));
        int lx = this.x0 + 6, ly = this.y0 + 1, lz = this.z0 + 6;
        int light = mc.theWorld.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Block, lx, ly, lz);
        int serverLight = server.getSavedLightValue(net.minecraft.world.EnumSkyBlock.Block, lx, ly, lz);
        net.minecraft.world.chunk.storage.ExtendedBlockStorage cs = BellowAPI
            .getDeepSection(mc.theWorld.getChunkFromBlockCoords(lx, lz), ly >> 4);
        net.minecraft.world.chunk.storage.ExtendedBlockStorage ss = BellowAPI
            .getDeepSection(server.getChunkFromBlockCoords(lx, lz), ly >> 4);
        int clientRaw = cs == null ? -1 : cs.getExtBlocklightValue(lx & 15, ly & 15, lz & 15);
        int serverRaw = ss == null ? -1 : ss.getExtBlocklightValue(lx & 15, ly & 15, lz & 15);
        this.check("server block light below Y 0", serverLight >= 13, "light=" + serverLight + " raw=" + serverRaw);
        this.check("client block light below Y 0", light >= 13, "light=" + light + " raw=" + clientRaw);
        this.check("player is below Y 0", mc.thePlayer.posY < 0, "posY=" + mc.thePlayer.posY);
    }

    private void shot(Minecraft mc, String name) {
        File dir = mc.mcDataDir;
        ScreenShotHelper.saveScreenshot(dir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        this.out.add("SHOT screenshots/" + name + ".png");
    }

    private void finish(Minecraft mc) {
        this.out.add(this.failures == 0 ? "RESULT PASS" : "RESULT FAIL (" + this.failures + ")");
        for (String line : this.out) AkashicBellow.LOG.info("[clienttest] {}", line);
        try {
            Files.write(
                new File(mc.mcDataDir, "akashicbellow-clienttest.txt").toPath(),
                this.out,
                StandardCharsets.UTF_8);
        } catch (Exception e) {
            AkashicBellow.LOG.error("Cannot write client test report", e);
        }
        this.state = 99;
        mc.shutdown();
    }
}
