package com.akashiic.bellow.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loads the early mixins. Mixins are always applied for the environment they belong to; whether a world actually has
 * deep layers is decided at runtime from the world floor, so worlds without deep layers run vanilla logic.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("AkashicBellowCore")
@IFMLLoadingPlugin.SortingIndex(1100)
public class BellowCoreMod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.akashicbellow.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<>();
        // storage and bounds
        mixins.add("common.MixinWorld_Deep");
        mixins.add("common.MixinChunk_Deep");
        mixins.add("common.MixinChunk_DeepLoad");
        mixins.add("common.MixinChunk_DeepTileEntity");
        mixins.add("common.MixinChunkCache_Deep");
        // lighting: exactly one of the two, matching ArchaicFix's own decision
        Compat.phosphor = EnvironmentDetector.isPhosphorActive(loadedCoreMods);
        if (Compat.phosphor) {
            mixins.add("common.MixinChunk_DeepLightPhosphor");
        } else {
            mixins.add("common.MixinChunk_DeepLightVanilla");
        }
        // network
        mixins.add("common.MixinPlayerInstance_Deep");
        mixins.add("common.PlayerManagerAccessor");
        mixins.add("common.MixinC07PacketPlayerDigging_SignedY");
        mixins.add("common.MixinC08PacketPlayerBlockPlacement_SignedY");
        mixins.add("common.MixinS28PacketEffect_SignedY");
        mixins.add("common.MixinS0APacketUseBed_SignedY");
        // gameplay
        mixins.add("common.MixinWorldServer_DeepTicks");
        mixins.add("common.MixinEntity_DeepVoid");
        mixins.add("common.MixinEntityMinecart_DeepVoid");
        mixins.add("common.MixinEntityFallingBlock_Deep");
        mixins.add("common.MixinBlockFalling_Deep");
        mixins.add("common.MixinBlockPistonBase_Deep");
        mixins.add("common.MixinBlockPlants_Deep");
        mixins.add("common.MixinPathFinder_Deep");
        mixins.add("common.MixinSpawnerAnimals_Deep");

        if (FMLLaunchHandler.side()
            .isClient()) {
            mixins.add("client.MixinWorld_DeepClient");
            mixins.add("client.MixinChunkCache_DeepClient");
            mixins.add("client.MixinEntityRenderer_DeepFog");
            mixins.add("client.MixinWorldClient_DeepParticles");
            // Angelica (Celeritas) replaces terrain rendering; it gets its own late compat mixins.
            if (!EnvironmentDetector.classExists("com.gtnewhorizons.angelica.AngelicaMod")) {
                mixins.add("client.MixinRenderGlobal_Deep");
            }
        }
        return mixins;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
