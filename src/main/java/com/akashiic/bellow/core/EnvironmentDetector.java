package com.akashiic.bellow.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

/** Early (coremod-time) detection of mods whose mixins change the shape of our targets. */
public final class EnvironmentDetector {

    private static final String ARCHAIC_ENGINE = "org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine";

    private EnvironmentDetector() {}

    public static boolean classExists(String name) {
        try {
            return Launch.classLoader.getClassBytes(name) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * ArchaicFix applies its Phosphor mixins when {@code enablePhosphor} is true (default) and Supernova is absent.
     * Read the flag from its config file: ArchaicFix may not have loaded its config yet.
     */
    public static boolean isPhosphorActive(Set<String> loadedCoreMods) {
        if (!classExists(ARCHAIC_ENGINE)) return false;
        if (loadedCoreMods.contains("com.mitchej123.supernova.core.SupernovaCore")) return false;
        File cfg = new File(Launch.minecraftHome, "config/archaicfix.cfg");
        if (!cfg.isFile()) return true;
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(new FileInputStream(cfg), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("B:enablePhosphor=")) return Boolean.parseBoolean(
                    line.substring("B:enablePhosphor=".length())
                        .trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + cfg, e);
        }
        return true;
    }
}
