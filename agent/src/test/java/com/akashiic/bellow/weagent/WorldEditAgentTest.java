package com.akashiic.bellow.weagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Needs the real jars (not redistributed): {@code -Pbellow.weJar=... -Pbellow.serverJar=...}; skipped otherwise.
 */
class WorldEditAgentTest {

    private static final String WE_JAR = System.getProperty("bellow.weJar");
    private static final String SERVER_JAR = System.getProperty("bellow.serverJar");

    /** Edits per class: a change here means a site was added or lost. */
    private static final Map<String, Integer> EDITS = new HashMap<>();

    static {
        String we = WorldEditPatches.WE;
        EDITS.put(we + "extent/validation/DataValidatorExtent", 2);
        EDITS.put(we + "regions/CuboidRegion", 2);
        EDITS.put(we + "regions/Polygonal2DRegion", 2);
        EDITS.put(we + "world/AbstractWorld", 1);
        EDITS.put(we + "bukkit/selections/Polygonal2DSelection", 2);
        EDITS.put(we + "bukkit/selections/CylinderSelection", 2);
        EDITS.put(we + "command/SelectionCommands", 5);
        EDITS.put(we + "command/UtilityCommands", 2);
        EDITS.put(we + "EditSession", 5);
        EDITS.put(we + "util/TargetBlock", 1);
        EDITS.put(we + "extension/platform/AbstractPlayerActor", 11);
    }

    @Test
    void everyTargetPatchesWithTheExpectedSites() throws Exception {
        assumeTrue(WE_JAR != null, "bellow.weJar not set");
        assertEquals(EDITS.keySet(), WorldEditPatches.TARGETS.keySet());
        try (JarFile jar = new JarFile(WE_JAR)) {
            for (Map.Entry<String, WorldEditPatches.Target> e : WorldEditPatches.TARGETS.entrySet()) {
                byte[] bytes = read(jar, e.getKey() + ".class");
                assertEquals(e.getValue().sha256, WorldEditTransformer.sha256(bytes), e.getKey());
                Patcher p = new Patcher(bytes);
                e.getValue().patch.apply(p);
                byte[] out = p.finish();
                assertEquals(EDITS.get(e.getKey()), p.edits(), e.getKey());
                assertThrows(IllegalStateException.class, () -> new Patcher(out), "second pass " + e.getKey());
            }
        }
    }

    /** {@code //chunk} without {@code -s}: the top corner is {@code min + maxY - floor}, not {@code min + maxY}. */
    @Test
    void chunkSelectionEndsAtTheTop() throws Exception {
        assumeTrue(WE_JAR != null, "bellow.weJar not set");
        String name = WorldEditPatches.WE + "command/SelectionCommands";
        byte[] out;
        try (JarFile jar = new JarFile(WE_JAR)) {
            Patcher p = new Patcher(read(jar, name + ".class"));
            WorldEditPatches.TARGETS.get(name).patch.apply(p);
            out = p.finish();
        }
        org.objectweb.asm.tree.ClassNode cls = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(out).accept(cls, 0);
        int corrected = 0;
        for (org.objectweb.asm.tree.MethodNode m : cls.methods) {
            if (!m.name.equals("chunk")) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof org.objectweb.asm.tree.MethodInsnNode)
                    || !((org.objectweb.asm.tree.MethodInsnNode) n).name.equals("getMaxY")) continue;
                org.objectweb.asm.tree.AbstractInsnNode load = n.getNext(), helper = load.getNext(), sub = helper.getNext();
                boolean floorSubtracted = load.getOpcode() == org.objectweb.asm.Opcodes.ALOAD
                    && helper instanceof org.objectweb.asm.tree.MethodInsnNode
                    && ((org.objectweb.asm.tree.MethodInsnNode) helper).name.equals(Patcher.HELPER)
                    && sub.getOpcode() == org.objectweb.asm.Opcodes.ISUB;
                if (floorSubtracted) corrected++;
            }
        }
        // two getMaxY calls in chunk(): -s uses it as an absolute Y (kept), the other as a height above the floor
        assertEquals(1, corrected);
    }

    /**
     * Crucible's plugin class loader remaps Guava ({@code remap-guava} in cauldron.yml) and re-serializes the class
     * before agents see it: the hash differs but every site must still be found.
     */
    @Test
    void patchesClassesRemappedByCrucible() throws Exception {
        assumeTrue(WE_JAR != null, "bellow.weJar not set");
        Remapper guava = new Remapper() {

            @Override
            public String map(String name) {
                return name.startsWith("com/google/common/") ? "guava10/" + name : name;
            }
        };
        try (JarFile jar = new JarFile(WE_JAR)) {
            for (Map.Entry<String, WorldEditPatches.Target> e : WorldEditPatches.TARGETS.entrySet()) {
                ClassWriter w = new ClassWriter(0);
                new ClassReader(read(jar, e.getKey() + ".class")).accept(new ClassRemapper(w, guava), 0);
                Patcher p = new Patcher(w.toByteArray());
                e.getValue().patch.apply(p);
                p.finish();
                assertEquals(EDITS.get(e.getKey()), p.edits(), e.getKey());
            }
        }
    }

    @Test
    void agentPatchesTheRealJar() throws Exception {
        assumeTrue(WE_JAR != null && SERVER_JAR != null, "bellow.weJar / bellow.serverJar not set");
        String out = runProbe(WE_JAR, "patched");
        assertTrue(out.contains("WorldEdit 6.1.9 verified"), out);
        assertTrue(!out.contains("CHECK FAIL") && !out.contains("rejected"), out);
        for (String name : WorldEditPatches.TARGETS.keySet()) {
            assertTrue(out.contains("Patched " + name.substring(name.lastIndexOf('/') + 1) + ":"), name + "\n" + out);
        }
    }

    @Test
    void agentLeavesAnotherBuildUntouched(@TempDir Path dir) throws Exception {
        assumeTrue(WE_JAR != null && SERVER_JAR != null, "bellow.weJar / bellow.serverJar not set");
        // same code, different bytes: TargetBlock rewritten by ASM
        File copy = dir.resolve("worldedit-other.jar")
            .toFile();
        String changed = WorldEditPatches.WE + "util/TargetBlock.class";
        try (JarFile in = new JarFile(WE_JAR); JarOutputStream out = new JarOutputStream(new FileOutputStream(copy))) {
            for (Enumeration<JarEntry> en = in.entries(); en.hasMoreElements();) {
                JarEntry e = en.nextElement();
                out.putNextEntry(new JarEntry(e.getName()));
                if (!e.isDirectory()) {
                    byte[] b = read(in, e.getName());
                    if (e.getName()
                        .equals(changed)) {
                        ClassWriter w = new ClassWriter(0);
                        new ClassReader(b).accept(w, 0);
                        b = w.toByteArray();
                    }
                    out.write(b);
                }
                out.closeEntry();
            }
        }
        String out = runProbe(copy.getPath(), "vanilla");
        assertTrue(out.contains("WorldEdit left untouched"), out);
        assertTrue(!out.contains("Patched ") && !out.contains("CHECK FAIL"), out);

        // The verdict belongs to the jar: another jar with the expected build is still patched in the same JVM.
        WorldEditTransformer t = new WorldEditTransformer();
        String cuboid = WorldEditPatches.WE + "regions/CuboidRegion";
        byte[] bytes;
        try (JarFile jar = new JarFile(WE_JAR)) {
            bytes = read(jar, cuboid + ".class");
        }
        assertNull(t.transform(null, cuboid, null, domain(copy), bytes), "class from the other build");
        assertNotNull(t.transform(null, cuboid, null, domain(new File(WE_JAR)), bytes), "class from WorldEdit 6.1.9");
    }

    private static ProtectionDomain domain(File jar) throws Exception {
        return new ProtectionDomain(new CodeSource(jar.toURI().toURL(), (Certificate[]) null), null);
    }

    private static String runProbe(String weJar, String mode) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String testClasses = new File(
            WorldEditProbeMain.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()).getPath();
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-javaagent:" + System.getProperty("bellow.agentJar"));
        cmd.add("-cp");
        cmd.add(testClasses);
        cmd.add(WorldEditProbeMain.class.getName());
        cmd.add(weJar);
        cmd.add(SERVER_JAR);
        cmd.add(mode);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
            .start();
        byte[] all;
        try (InputStream in = p.getInputStream()) {
            all = readAll(in);
        }
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        String out = new String(all, StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        return out;
    }

    private static byte[] read(JarFile jar, String name) throws Exception {
        try (InputStream in = jar.getInputStream(jar.getJarEntry(name))) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) >= 0;) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
