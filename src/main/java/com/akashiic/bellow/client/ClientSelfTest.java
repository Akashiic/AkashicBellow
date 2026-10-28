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
 * screenshots (frames across Y 0, the sky seen up an open pit at several heights), then quits. Report:
 * {@code akashicbellow-clienttest.txt} in the game directory.
 */
public final class ClientSelfTest {

    private static final String SAVE = "bellow-clienttest";
    private final List<String> out = new ArrayList<>();
    private int state;
    private int timer;
    private int failures;
    private volatile boolean serverReady;
    private int x0, y0, z0;
    /** A sign written below 0 in a chunk the client has not received yet (description packet on first send). */
    private int sx, sy, sz;
    private volatile boolean signRequested;
    private volatile boolean signReady;

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
                        this.signRequested = true;
                        this.state = 6;
                        this.timer = 0;
                    }
                    break;
                case 6: // walk into the chunk with the deep sign: its text arrives with the chunk
                    if (this.signReady && ++this.timer > 200) {
                        Object sign = mc.theWorld.getChunkFromBlockCoords(this.sx, this.sz).chunkTileEntityMap
                            .get(new net.minecraft.world.ChunkPosition(this.sx & 15, this.sy, this.sz & 15));
                        String text = sign instanceof net.minecraft.tileentity.TileEntitySign
                            ? ((net.minecraft.tileentity.TileEntitySign) sign).signText[0]
                            : null;
                        net.minecraft.tileentity.TileEntity serverSign = MinecraftServer.getServer()
                            .worldServerForDimension(0)
                            .getTileEntity(this.sx, this.sy, this.sz);
                        this.check(
                            "deep tile entity state sent with the chunk",
                            "bellow".equals(text),
                            "sign text on client: " + text
                                + ", client block "
                                + Block.blockRegistry.getNameForObject(mc.theWorld.getBlock(this.sx, this.sy, this.sz))
                                + ", client chunk loaded "
                                + mc.theWorld.getChunkFromBlockCoords(this.sx, this.sz).isChunkLoaded
                                + ", player at "
                                + (int) mc.thePlayer.posX
                                + ","
                                + (int) mc.thePlayer.posY
                                + ","
                                + (int) mc.thePlayer.posZ
                                + ", server "
                                + (serverSign instanceof net.minecraft.tileentity.TileEntitySign
                                    ? ((net.minecraft.tileentity.TileEntitySign) serverSign).signText[0]
                                    : String.valueOf(serverSign)));
                        this.boundaryRequested = true;
                        this.state = 7;
                        this.timer = 0;
                    }
                    break;
                case 7: // room across Y 0 (red wool below, blue above): wait until the client has it, then meshing
                    if (this.boundaryReady && mc.theWorld.getBlock(this.bx, -3, this.bz + 6) == Blocks.wool
                        && mc.theWorld.getBlock(this.bx, 3, this.bz + 6) == Blocks.wool
                        && ++this.timer > 200) {
                        mc.gameSettings.hideGUI = true;
                        mc.gameSettings.viewBobbing = false;
                        mc.thePlayer.capabilities.isFlying = true;
                        this.state = 8;
                        this.timer = 0;
                    }
                    break;
                case 8: // frames 1: still camera, eye just below Y 0
                case 9: // frames 2: rising through Y 0
                case 10: // frames 3: turning
                case 11: // frames 4: still camera while deep and surface blocks change (section rebuilds)
                    this.frameStep(mc);
                    break;
                case 12: // sky seen from an open pit at several heights, down to the deep layer
                    this.skyStep(mc);
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

    // Frame capture across Y 0 (analysed by tools/clientframes.py): every rendered frame of states 8..11 is saved.
    private int bx, bz;
    private volatile boolean boundaryRequested;
    private volatile boolean boundaryReady;
    private volatile boolean editing;
    private int frame;
    private static final String[] SEQUENCES = { "still", "rise", "turn", "edit" };
    private static final int SEQUENCE_TICKS = 44;
    /** Ticks at the start of each sequence where the camera is placed but no frame is kept. */
    private static final int SETTLE_TICKS = 10;
    private volatile boolean capturing;
    private volatile boolean camActive;
    private volatile double camY;
    private volatile float camYaw;

    private void frameStep(Minecraft mc) {
        int t = Math.max(0, this.timer++ - SETTLE_TICKS);
        this.capturing = this.timer > SETTLE_TICKS;
        double y = -1.0;
        float yaw = 0.0F;
        if (this.state == 9) y = -6.0 + 0.25 * t;
        if (this.state == 10) yaw = -60.0F + 120.0F * t / SEQUENCE_TICKS;
        this.editing = this.state == 11;
        // The server moves the player (one authority for the camera: a client-side move would be corrected by the
        // server a tick later and show up as camera jitter, not as a rendering problem).
        this.camY = y;
        this.camYaw = yaw;
        this.camActive = true;
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        if (this.timer >= SEQUENCE_TICKS + SETTLE_TICKS) {
            this.capturing = false;
            this.out.add("FRAMES " + SEQUENCES[this.state - 8] + " " + this.frame);
            this.frame = 0;
            this.timer = 0;
            if (++this.state > 11) {
                this.editing = false;
                this.camActive = false;
                this.skyRequested = true;
            }
        }
    }

    // Sky check: a pit open from the floor to the sky, camera looking straight up (noon: the sun is overhead).
    private static final int[] SKY_HEIGHTS = { 100, 40, 10, -10, -40 };
    private int px, pz;
    private volatile boolean skyRequested;
    private volatile boolean skyReady;
    private int skyIndex;
    private volatile float camPitch;

    private void skyStep(Minecraft mc) {
        if (!this.skyReady) return;
        this.camActive = true; // brings the player to the pit
        if (mc.theWorld.getBlock(this.px + 13, -40, this.pz) == Blocks.air
            || mc.theWorld.getBlock(this.px, -40, this.pz) != Blocks.air) {
            this.camY = SKY_HEIGHTS[0];
            this.camPitch = -90.0F;
            return;
        }
        if (this.timer == 0) {
            mc.gameSettings.fovSetting = 30.0F;
            this.camY = SKY_HEIGHTS[this.skyIndex];
            this.camPitch = -90.0F;
            this.camYaw = 0.0F;
        }
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        if (++this.timer > (this.skyIndex == 0 ? 200 : 60)) {
            this.shot(mc, "bellow-sky-" + SKY_HEIGHTS[this.skyIndex]);
            this.timer = 0;
            if (++this.skyIndex >= SKY_HEIGHTS.length) {
                this.camActive = false;
                mc.gameSettings.fovSetting = 70.0F;
                mc.gameSettings.hideGUI = false;
                this.finish(mc);
            }
        }
    }

    /** 25 x 25 pit, open from just above the floor to the sky, far from where the client has been. */
    private void buildSkyPit() {
        WorldServer w = MinecraftServer.getServer()
            .worldServerForDimension(0);
        int minY = BellowAPI.getMinY(w);
        this.px = this.bx + 400;
        this.pz = this.bz;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            w.theChunkProviderServer.loadChunk((this.px >> 4) + dx, (this.pz >> 4) + dz);
        for (int y = 255; y >= minY + 4; y--) for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
            if (w.getBlock(this.px + dx, y, this.pz + dz) != Blocks.air)
                w.setBlock(this.px + dx, y, this.pz + dz, Blocks.air, 0, 2);
        }
        // a marker in the wall: the client has the pit once it sees this stone and air at the centre
        for (int y = minY + 4; y < 0; y++) w.setBlock(this.px + 13, y, this.pz, Blocks.stone, 0, 2);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !this.capturing || this.state < 8 || this.state > 11) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(
            mc.mcDataDir,
            String.format("frames-%s-%03d.png", SEQUENCES[this.state - 8], this.frame++),
            mc.displayWidth,
            mc.displayHeight,
            mc.getFramebuffer());
    }

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
        if (event.phase == TickEvent.Phase.END && this.skyRequested && !this.skyReady) {
            this.buildSkyPit();
            this.skyReady = true;
            return;
        }
        if (event.phase == TickEvent.Phase.END && this.boundaryRequested && !this.boundaryReady) {
            this.buildBoundaryRoom();
            this.boundaryReady = true;
            return;
        }
        if (event.phase == TickEvent.Phase.END && this.camActive) {
            EntityPlayerMP p = (EntityPlayerMP) MinecraftServer.getServer()
                .worldServerForDimension(0).playerEntities.get(0);
            if (this.state == 12) {
                p.playerNetServerHandler
                    .setPlayerLocation(this.px + 0.5, this.camY, this.pz + 0.5, this.camYaw, this.camPitch);
            } else {
                p.playerNetServerHandler.setPlayerLocation(this.bx + 0.5, this.camY, this.bz - 3.5, this.camYaw, 0.0F);
            }
        }
        if (event.phase == TickEvent.Phase.END && this.editing
            && MinecraftServer.getServer()
                .getTickCounter() % 4 == 0) {
            WorldServer w = MinecraftServer.getServer()
                .worldServerForDimension(0);
            boolean on = MinecraftServer.getServer()
                .getTickCounter() % 8 == 0;
            w.setBlock(this.bx + 2, -3, this.bz + 6, Blocks.wool, on ? 0 : 14, 3);
            w.setBlock(this.bx - 2, 2, this.bz + 6, Blocks.wool, on ? 0 : 11, 3);
            return;
        }
        if (event.phase == TickEvent.Phase.END && this.signRequested && !this.signReady) {
            EntityPlayerMP p = (EntityPlayerMP) MinecraftServer.getServer()
                .worldServerForDimension(0).playerEntities.get(0);
            p.playerNetServerHandler.setPlayerLocation(this.sx + 0.5, this.sy, this.sz - 1.5, 0.0F, 0.0F);
            this.signReady = true;
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
        // far away (not sent to the client yet): a deep sign with text, in a small lit pocket
        this.sx = this.x0 + 320;
        this.sz = this.z0;
        this.sy = minY + 20;
        // load explicitly: provideChunk hands out the empty chunk for unloaded coordinates here
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            world.theChunkProviderServer.loadChunk((this.sx >> 4) + dx, (this.sz >> 4) + dz);
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy < 4; dy++)
            for (int dz = -2; dz <= 1; dz++) world.setBlockToAir(this.sx + dx, this.sy + dy, this.sz + dz);
        world.setBlock(this.sx, this.sy + 3, this.sz - 1, Blocks.glowstone);
        world.setBlock(this.sx, this.sy, this.sz, Blocks.standing_sign, 8, 3);
        net.minecraft.tileentity.TileEntity sign = world.getTileEntity(this.sx, this.sy, this.sz);
        if (sign instanceof net.minecraft.tileentity.TileEntitySign) {
            ((net.minecraft.tileentity.TileEntitySign) sign).signText[0] = "bellow";
            sign.markDirty();
        }
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

    /** 13 x 15 x 13 glowstone room from Y -8 to Y 8; the wall facing the player is red wool below 0, blue above. */
    private void buildBoundaryRoom() {
        WorldServer w = MinecraftServer.getServer()
            .worldServerForDimension(0);
        // next to the sign pocket, where the client already is: the surrounding chunks are loaded and meshed
        this.bx = this.sx;
        this.bz = this.sz + 30;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            w.theChunkProviderServer.loadChunk((this.bx >> 4) + dx, (this.bz >> 4) + dz);
        for (int dx = -6; dx <= 6; dx++) for (int y = -8; y <= 8; y++) for (int dz = -6; dz <= 6; dz++) {
            boolean shell = Math.abs(dx) == 6 || Math.abs(dz) == 6 || Math.abs(y) == 8;
            Block block = !shell ? Blocks.air : dz == 6 ? Blocks.wool : Blocks.glowstone;
            int meta = block == Blocks.wool ? y < 0 ? 14 : 11 : 0;
            w.setBlock(this.bx + dx, y, this.bz + dz, block, meta, 2);
        }
        EntityPlayerMP p = (EntityPlayerMP) w.playerEntities.get(0);
        p.playerNetServerHandler.setPlayerLocation(this.bx + 0.5, -1.0, this.bz - 3.5, 0.0F, 0.0F);
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
