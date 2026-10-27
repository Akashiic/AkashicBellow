package com.akashiic.bellow;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.gen.DeepBlocks;
import com.akashiic.bellow.gen.DeepDecorator;
import com.akashiic.bellow.net.DimensionInfoMessage;
import com.akashiic.bellow.storage.DeepDataManager;
import com.akashiic.bellow.world.DeepWorlds;
import com.falsepattern.chunk.api.DataRegistry;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = AkashicBellow.MODID,
    name = "Akashic Bellow",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:chunkapi@[0.8.4,);after:endlessids;after:etfuturum;after:archaicfix;after:hodgepodge;after:angelica")
public class AkashicBellow {

    public static final String MODID = "akashicbellow";
    public static final Logger LOG = LogManager.getLogger("AkashicBellow");

    public static SimpleNetworkWrapper network;

    static {
        try {
            ConfigurationManager.registerConfig(BellowConfig.class);
            ConfigurationManager.registerConfig(GenConfig.class);
        } catch (ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(DimensionInfoMessage.Handler.class, DimensionInfoMessage.class, 0, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        if (event.getSide()
            .isClient()) {
            FMLCommonHandler.instance()
                .bus()
                .register(new ClientEvents());
            if (Boolean.getBoolean("akashicbellow.clienttest")) com.akashiic.bellow.client.ClientSelfTest.register();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new WorldEvents());
        // Registered after EndlessIDs (dependency ordering), so the per-section packet budget already accounts for it.
        DataRegistry.registerDataManager(new DeepDataManager());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        DeepBlocks.resolve();
        DeepDecorator.resolve();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BellowCommand());
    }

    /** Headless verification: {@code -Dakashicbellow.selftest=true [-Dakashicbellow.selftest.exit=true]}. */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("akashicbellow.selftest")) return;
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        String phase = System.getProperty("akashicbellow.selftest.phase", "full");
        if (phase.equals("bench")) {
            com.akashiic.bellow.selftest.Bench.run(server);
            return;
        }
        if (phase.startsWith("compat")) {
            com.akashiic.bellow.selftest.CompatProbe.start(server, phase);
            return;
        }
        SelfTest test = new SelfTest(server.worldServerForDimension(0));
        java.util.List<String> lines = test.run();
        for (String line : lines) LOG.info("[selftest] {}", line);
        try {
            java.nio.file.Files.write(
                new java.io.File("akashicbellow-selftest.txt").toPath(),
                lines,
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            LOG.error("Cannot write self test report", e);
        }
        if (Boolean.getBoolean("akashicbellow.selftest.exit")) server.initiateShutdown();
    }

    @Mod.EventHandler
    public void serverStopping(cpw.mods.fml.common.event.FMLServerStoppingEvent event) {
        com.akashiic.bellow.selftest.CompatProbe.onServerStopping();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        DeepWorlds.clearCache();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        announce(event.player.worldObj, event.player);
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        announce(event.player.worldObj, event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        announce(event.player.worldObj, event.player);
    }

    private static void announce(World world, net.minecraft.entity.player.EntityPlayer player) {
        if (world == null || !(player instanceof EntityPlayerMP)) return;
        network.sendTo(
            new DimensionInfoMessage(world.provider.dimensionId, BellowAPI.getMinY(world)),
            (EntityPlayerMP) player);
    }
}
