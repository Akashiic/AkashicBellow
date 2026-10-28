package com.akashiic.bellow;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = AkashicBellow.MODID, category = "worldgen")
public class GenConfig {

    @Config.Comment("Generate the deep layer in chunks. Disable only for debugging: chunks stay void below Y 0.")
    @Config.DefaultBoolean(true)
    public static boolean enabled;

    @Config.Comment("Also fill chunks that were saved before the mod was installed (retrogen).")
    @Config.DefaultBoolean(true)
    public static boolean retrogen;

    @Config.Comment({
        "Replace natural bedrock at Y 0..legacyBedrockMaxY with the deep block so the old floor opens into",
        "the new layer (what 1.18 did when upgrading worlds). Only Blocks.bedrock is touched." })
    @Config.DefaultBoolean(true)
    public static boolean replaceLegacyBedrock;

    @Config.DefaultInt(4)
    @Config.RangeInt(min = 0, max = 8)
    public static int legacyBedrockMaxY;

    @Config.Comment({ "Block that fills the deep layer, as modid:name[:meta].",
        "'auto' = etfuturum:deepslate when Et Futurum Requiem provides it, otherwise minecraft:stone." })
    @Config.DefaultString("auto")
    public static String deepBlock;

    @Config.Comment("Generate 1.18-style ores, tuff and gravel below Y 0.")
    @Config.DefaultBoolean(true)
    public static boolean ores;

    @Config.Comment({ "For floors other than -64: scale the count of features whose range starts at the bottom",
        "of the world so that ore density per block stays equal to 1.18." })
    @Config.DefaultBoolean(true)
    public static boolean scaleBottomAnchoredCounts;

    @Config.Comment("Global multiplier applied to every deep ore count (expected value; fractional counts are stochastic).")
    @Config.DefaultDouble(1.0)
    @Config.RangeDouble(min = 0.0, max = 16.0)
    public static double oreCountMultiplier;
}
