package com.akashiic.bellow.weagent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent that lets WorldEdit 6.1.9 (Bukkit) work below Y 0 on a Crucible server running Akashic Bellow.
 * <p>
 * Plugin classes are loaded by Bukkit's PluginClassLoader, which mixins cannot reach, so the patch is applied when the
 * class is defined. Only the exact WorldEdit 6.1.9 build is patched: every target class is checked by SHA-256 before
 * anything is changed, and on any mismatch the agent leaves all of WorldEdit untouched.
 * <p>
 * The floor of each world comes from the Bellow mod, which publishes it as the system property
 * {@code akashicbellow.floor.<Bukkit world name>}. Without the mod (or for worlds without a deep layer) the property is
 * absent and WorldEdit keeps its vanilla floor of 0.
 */
public final class AgentMain {

    static final String TAG = "[AkashicBellow-WE] ";

    private AgentMain() {}

    public static void premain(String args, Instrumentation inst) {
        System.err.println(TAG + version() + " armed; exact WorldEdit 6.1.9 Y floor patch only.");
        inst.addTransformer(new WorldEditTransformer(), false);
    }

    static String version() {
        String v = AgentMain.class.getPackage()
            .getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
