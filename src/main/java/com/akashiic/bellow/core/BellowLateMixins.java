package com.akashiic.bellow.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Mixins into other mods, selected by the mods that are actually loaded. */
@LateMixin
public class BellowLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.akashicbellow.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        Compat.angelica = FMLLaunchHandler.side()
            .isClient() && loadedMods.contains("angelica");
        if (Compat.angelica) {
            mixins.add("angelica.MixinCeleritasWorldRenderer_Deep");
            mixins.add("angelica.MixinAngelicaRenderSectionManager_Deep");
            mixins.add("angelica.MixinClonedChunkSection_Deep");
            mixins.add("angelica.MixinWorldSlice_Deep");
            mixins.add("angelica.MixinCompatMathHelper_Deep");
        }
        return mixins;
    }
}
