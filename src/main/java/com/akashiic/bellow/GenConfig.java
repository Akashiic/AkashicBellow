package com.akashiic.bellow;

import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = AkashicBellow.MODID, category = "worldgen")
public class GenConfig {

    @Config.Comment({ "Run the deep generation stages (the registered generators, the light stage). Disable only for",
        "debugging: new chunks then get no deep layer." })
    @Config.DefaultBoolean(true)
    public static boolean enabled;

    @Config.Comment("Also hand chunks saved before they had a deep layer to the generators (retrogen).")
    @Config.DefaultBoolean(true)
    public static boolean retrogen;

    @Config.Comment({
        "Replace natural bedrock at Y 0..legacyBedrockMaxY with deepslate (Et Futurum) or stone so the old floor",
        "opens into the new layer (what 1.18 did when upgrading worlds), after the generators made it and only where",
        "they left Y -1 solid. Only Blocks.bedrock is touched." })
    @Config.DefaultBoolean(true)
    public static boolean replaceLegacyBedrock;

    @Config.DefaultInt(4)
    @Config.RangeInt(min = 0, max = 8)
    public static int legacyBedrockMaxY;

    @Config.Comment({ "Dimensions with a deep layer but no deep generator (none installed, or none that applies):",
        "give new chunks a provisional layer of stone over a bedrock floor (no ores, no caves), so nothing below 0",
        "is void next to chunks that have a deep layer. Those chunks keep their natural bedrock at Y 0 and count as",
        "not generated: a generator installed later replaces the provisional layer. Off: they get no deep layer." })
    @Config.DefaultBoolean(true)
    public static boolean provisionalLayer;

    @Config.Comment({ "Time per world tick for the deep stages that run after the world tick: the light of what deep",
        "generators placed and their onDecorated/onLit callbacks. What does not fit waits for the next tick." })
    @Config.DefaultInt(10)
    @Config.RangeInt(min = 1, max = 1000)
    public static int stageBudgetMillis;

    @Config.Comment({
        "Debugging deep generators: refuse (and log) World writes that their contract forbids while their",
        "terrain or decoration step runs. Off in normal play." })
    @Config.DefaultBoolean(false)
    public static boolean strictGenerators;
}
