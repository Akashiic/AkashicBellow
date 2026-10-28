package com.akashiic.bellow.weagent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.net.URL;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Patches the WorldEdit classes listed in {@link WorldEditPatches} as they are defined.
 * <p>
 * All or nothing per jar: when the first target class of a jar is defined, every target entry of that jar is hashed.
 * If any differs from WorldEdit 6.1.9, no class from that jar is patched (a half-patched WorldEdit would select below
 * 0 and then refuse to edit there, or the reverse). The verdict is kept per jar, so a plugin that bundles its own copy
 * of WorldEdit classes is judged on its own bytes. Any failure keeps the original bytecode of that class.
 */
final class WorldEditTransformer implements ClassFileTransformer {

    private static final String TAG = AgentMain.TAG;

    /** Verdict per defining jar (code source location): whether it is the expected build. */
    private final Map<String, Boolean> verdicts = new HashMap<>();

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain,
        byte[] bytes) {
        if (redefined != null || bytes == null || name == null) return null;
        WorldEditPatches.Target target = WorldEditPatches.TARGETS.get(name);
        if (target == null) return null;
        try {
            if (!enabled(domain)) return null;
            // On Crucible the plugin class loader remaps classes before agents see them (SpecialSource with
            // "remap-guava" in cauldron.yml renames com/google/common to guava10/...), so the bytes may differ from
            // the verified jar entry. The jar is already proven to be WorldEdit 6.1.9; each site must still match
            // exactly, with its expected count.
            String sha = sha256(bytes);
            String origin = target.sha256.equals(sha) ? ""
                : " (already transformed upstream, sha256 " + sha + ")" + dump(name, bytes);
            Patcher p = new Patcher(bytes);
            target.patch.apply(p);
            byte[] out = p.finish();
            report("Patched " + name.substring(name.lastIndexOf('/') + 1) + ": " + p.edits() + " sites" + origin + ".");
            return out;
        } catch (ThreadDeath | VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            // The jar was verified, so another transformer changed this class: the rest of WorldEdit may already be
            // patched, which leaves it half-patched (selects below 0 but refuses to edit there, or the reverse).
            report(
                "Patch rejected for " + name
                    + "; original bytecode retained: "
                    + t
                    + ". WorldEdit is now only partly patched below Y 0: remove the transformer that changes this class"
                    + " or the agent."
                    + dump(name, bytes));
            return null;
        }
    }

    private synchronized boolean enabled(ProtectionDomain domain) {
        CodeSource source = domain == null ? null : domain.getCodeSource();
        URL url = source == null ? null : source.getLocation();
        String key = String.valueOf(url);
        Boolean verdict = this.verdicts.get(key);
        if (verdict == null) {
            String problem = verifyJar(url);
            verdict = problem == null;
            this.verdicts.put(key, verdict);
            report(
                problem == null
                    ? "WorldEdit 6.1.9 verified (" + WorldEditPatches.TARGETS.size() + " classes in " + url + "); patching."
                    : problem + "; WorldEdit left untouched.");
        }
        return verdict;
    }

    /** null when every target entry of the jar at {@code url} has the expected hash, else what is wrong. */
    static String verifyJar(URL url) {
        if (url == null) return "Cannot locate the WorldEdit jar";
        File file;
        try {
            file = new File(url.toURI());
        } catch (Exception e) {
            return "Cannot open the WorldEdit jar at " + url;
        }
        try (JarFile jar = new JarFile(file)) {
            for (Map.Entry<String, WorldEditPatches.Target> e : WorldEditPatches.TARGETS.entrySet()) {
                JarEntry entry = jar.getJarEntry(e.getKey() + ".class");
                if (entry == null) return "Not WorldEdit 6.1.9: " + e.getKey() + " is missing in " + file.getName();
                String sha;
                try (InputStream in = jar.getInputStream(entry)) {
                    sha = sha256(readAll(in));
                }
                if (!e.getValue().sha256.equals(sha)) {
                    return "Not WorldEdit 6.1.9: " + e.getKey() + " differs in " + file.getName() + " (" + sha + ")";
                }
            }
        } catch (Exception e) {
            return "Cannot read " + file + ": " + e;
        }
        return null;
    }

    /** Diagnostics: {@code -Dakashicbellow.we.dump=<dir>} writes the bytes of a class that arrived
     * transformed or could not be patched. */
    private static String dump(String name, byte[] bytes) {
        String dir = System.getProperty("akashicbellow.we.dump");
        if (dir == null) return "";
        File out = new File(dir, name.replace('/', '.') + ".class");
        try {
            out.getParentFile()
                .mkdirs();
            java.nio.file.Files.write(out.toPath(), bytes);
            return " Bytes written to " + out;
        } catch (Exception e) {
            return " Dump failed: " + e;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) >= 0;) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static String sha256(byte[] bytes) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256")
            .digest(bytes);
        StringBuilder s = new StringBuilder(64);
        for (byte b : d) s.append(String.format("%02x", b & 0xFF));
        return s.toString();
    }

    private static void report(String message) {
        System.err.println(TAG + message);
    }
}
