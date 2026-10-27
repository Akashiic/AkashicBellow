package com.akashiic.bellow;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = AkashicBellow.MODID, category = "general")
@Config.Comment("Deep world layers. The floor of a world is locked in <world>/akashicbellow.dat on first use.")
public class BellowConfig {

    @Config.Comment({ "Lowest block Y (inclusive) for enabled dimensions. Rounded down to a multiple of 16.",
        "Only applies to worlds that have not been locked yet. -64 matches Minecraft 1.18+.",
        "Each 16 blocks costs one extra chunk section (~10 KB of RAM per loaded chunk)." })
    @Config.DefaultInt(-64)
    @Config.RangeInt(min = -512, max = -16)
    @Config.RequiresWorldRestart
    public static int minY;

    @Config.Comment("Dimension ids that receive deep layers.")
    @Config.DefaultIntList({ 0 })
    @Config.RequiresWorldRestart
    public static int[] dimensions;

    @Config.Comment("Natural mob spawning also picks heights below Y 0 (like 1.18+).")
    @Config.DefaultBoolean(true)
    public static boolean spawnMobsInDeep;

    public static boolean isDimensionEnabled(int dim) {
        if (dimensions == null) return false;
        for (int d : dimensions) {
            if (d == dim) return true;
        }
        return false;
    }
}
