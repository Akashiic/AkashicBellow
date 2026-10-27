package com.akashiic.bellow.weagent;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs in a separate JVM started with the agent: loads WorldEdit from its jar (so the transformer sees the real code
 * source), links every target class (bytecode verification) and checks the patched behaviour through reflection.
 * <p>
 * Usage: {@code <worldedit jar> <server jar> patched|vanilla}. Prints {@code CHECK ok|FAIL <name> <detail>} lines and
 * exits with status 1 if any check failed.
 */
public final class WorldEditProbeMain {

    private static ClassLoader loader;
    private static boolean failed;

    public static void main(String[] args) throws Exception {
        loader = new URLClassLoader(
            new URL[] { new File(args[0]).toURI()
                .toURL(),
                new File(args[1]).toURI()
                    .toURL() },
            null);
        boolean patched = args[2].equals("patched");
        int floor = patched ? -64 : 0;
        System.setProperty("akashicbellow.floor.deep", "-64");
        System.setProperty("akashicbellow.floor.null", "-64");

        for (String name : WorldEditPatches.TARGETS.keySet()) {
            Class.forName(name.replace('/', '.'), true, loader);
        }
        check("all targets linked", true, "");

        Object deep = world("deep"), plain = world("plain");

        Object cuboid = make("regions.CuboidRegion", ws(), deep, vec(0, -100, 0), vec(3, 300, 3));
        check("cuboid clamps to floor", minY(cuboid) == floor, "minY=" + minY(cuboid));
        check("cuboid keeps top", maxY(cuboid) == 255, "maxY=" + maxY(cuboid));
        Object cuboidPlain = make("regions.CuboidRegion", ws(), plain, vec(0, -100, 0), vec(3, 10, 3));
        check("cuboid without floor property", minY(cuboidPlain) == 0, "minY=" + minY(cuboidPlain));
        Object cuboidNull = make("regions.CuboidRegion", ws(), null, vec(0, -100, 0), vec(3, 10, 3));
        check("cuboid without world", minY(cuboidNull) == 0, "minY=" + minY(cuboidNull));

        List<Object> points = new ArrayList<>();
        points.add(make("BlockVector2D", new Class<?>[] { int.class, int.class }, 0, 0));
        points.add(make("BlockVector2D", new Class<?>[] { int.class, int.class }, 5, 0));
        points.add(make("BlockVector2D", new Class<?>[] { int.class, int.class }, 0, 5));
        Object poly = make(
            "regions.Polygonal2DRegion",
            new Class<?>[] { cls("world.World"), List.class, int.class, int.class },
            deep,
            points,
            -100,
            10);
        check("polygon clamps to floor", minY(poly) == floor, "minY=" + minY(poly));

        Object nullWorld = cls("world.NullWorld").getMethod("getInstance")
            .invoke(null);
        Object min = cls("world.AbstractWorld").getMethod("getMinimumPoint")
            .invoke(nullWorld);
        int worldMin = (Integer) min.getClass()
            .getMethod("getBlockY")
            .invoke(min);
        check("world minimum point", worldMin == floor, "y=" + worldMin);

        Object extent = Proxy.newProxyInstance(loader, new Class<?>[] { cls("extent.Extent") }, (p, m, a) -> {
            if (m.getName()
                .equals("setBlock")) return true;
            return defaultValue(m.getReturnType());
        });
        Object validator = make(
            "extent.validation.DataValidatorExtent",
            new Class<?>[] { cls("extent.Extent"), cls("world.World") },
            extent,
            deep);
        Object stone = make("blocks.BaseBlock", new Class<?>[] { int.class }, 1);
        check("validator at floor", set(validator, vec(0, -64, 0), stone) == patched, "y=-64");
        check("validator below floor", !set(validator, vec(0, -65, 0), stone), "y=-65");
        check("validator at 0", set(validator, vec(0, 0, 0), stone), "y=0");
        check("validator at top", set(validator, vec(0, 255, 0), stone) && !set(validator, vec(0, 256, 0), stone), "");
        Object plainValidator = make(
            "extent.validation.DataValidatorExtent",
            new Class<?>[] { cls("extent.Extent"), cls("world.World") },
            extent,
            plain);
        check("validator without floor property", !set(plainValidator, vec(0, -1, 0), stone), "y=-1");

        System.exit(failed ? 1 : 0);
    }

    private static void check(String name, boolean ok, String detail) {
        if (!ok) failed = true;
        System.out.println("CHECK " + (ok ? "ok" : "FAIL") + " " + name + " " + detail);
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName("com.sk89q.worldedit." + name, false, loader);
    }

    private static Class<?>[] ws() throws ClassNotFoundException {
        return new Class<?>[] { cls("world.World"), cls("Vector"), cls("Vector") };
    }

    private static Object make(String name, Class<?>[] types, Object... args) throws Exception {
        Constructor<?> c = cls(name).getConstructor(types);
        return c.newInstance(args);
    }

    private static Object vec(int x, int y, int z) throws Exception {
        return make("Vector", new Class<?>[] { int.class, int.class, int.class }, x, y, z);
    }

    private static int minY(Object region) throws Exception {
        return blockY(
            region.getClass()
                .getMethod("getMinimumPoint")
                .invoke(region));
    }

    private static int maxY(Object region) throws Exception {
        return blockY(
            region.getClass()
                .getMethod("getMaximumPoint")
                .invoke(region));
    }

    private static int blockY(Object vector) throws Exception {
        return (Integer) vector.getClass()
            .getMethod("getBlockY")
            .invoke(vector);
    }

    private static boolean set(Object extent, Object pos, Object block) throws Exception {
        return (Boolean) extent.getClass()
            .getMethod("setBlock", cls("Vector"), cls("blocks.BaseBlock"))
            .invoke(extent, pos, block);
    }

    private static Object world(String name) throws Exception {
        return Proxy.newProxyInstance(loader, new Class<?>[] { cls("world.World") }, (p, m, a) -> {
            switch (m.getName()) {
                case "getName":
                    return name;
                case "getMaxY":
                    return 255;
                case "isValidBlockType":
                    return true;
                case "hashCode":
                    return System.identityHashCode(p);
                case "equals":
                    return p == a[0];
                case "toString":
                    return "World(" + name + ")";
                default:
                    return defaultValue(m.getReturnType());
            }
        });
    }

    private static Object defaultValue(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == double.class) return 0D;
        if (t == float.class) return 0F;
        return null;
    }
}
